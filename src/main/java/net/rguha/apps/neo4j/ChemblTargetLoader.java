package net.rguha.apps.neo4j;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

import java.io.File;
import java.sql.*;

/**
 * @author Rajarshi Guha
 */
public class ChemblTargetLoader {

    private final GraphDatabaseService gdb;

    class Target {
        public String id, name, acc;
        public TargetType type;
        public Integer taxId;

        public Node toNode(GraphDatabaseService gdb) {
            Node node = gdb.createNode(NodeType.TargetBiology);
            node.setProperty("created", new java.util.Date().getTime());
            node.setProperty("TargetType", type.toString());
            node.setProperty("TargetID", id);
            node.setProperty("TargetName", name);
            if (acc != null) node.setProperty("UniProtId", acc);
            return (node);
        }
    }

    public enum TargetType {
        MolecularTarget,
        TargetFamily
    }

    public enum NodeType implements Label {
        TargetBiology,
        Agent
    }

    public enum EdgeType implements RelationshipType {
        IsA,
        ChildOf
    }

    public ChemblTargetLoader(GraphDatabaseService gdb) {
        this.gdb = gdb;
    }

    public Node addNodeIfAbsent(Target t, String indexName, String key, Object value) {
        Index<Node> index = gdb.index().forNodes(indexName);
        Node hit = index.get(key, value).getSingle();
        if (hit != null) return hit;
        Node n = t.toNode(gdb);
        try (Transaction tx = gdb.beginTx()) {
            index.putIfAbsent(n, key, value);
            tx.success();
        }
        return n;
    }

    public void addChemblTargetClasses(String[] classes) {
        // i+1'th element is childOf i'th element
        for (int i = 0; i < classes.length; i++) {
            if (classes[i] == null) continue;
            Target t = new Target();
            t.id = classes[i];
            t.name = classes[i];
            t.type = TargetType.TargetFamily;
            Node node = addNodeIfAbsent(t, "node.chembl.TargetID", "TargetID", t.id);

            if (i > 0) { // make link to parent (ie preceding class)
                Index<Node> index = gdb.index().forNodes("node.chembl.TargetID");
                try (IndexHits<Node> hits = index.get("TargetID", classes[i - 1])) {
                    for (Node n : hits) {
                        if (n.equals(node)) continue;
                        // get relationships for this hit, ignore any contain the current node
                        Iterable<Relationship> rels = n.getRelationships(EdgeType.ChildOf, Direction.INCOMING);
                        boolean edgeExists = false;
                        for (Relationship r : rels) {
                            if (r.getOtherNode(n).equals(node)) {
                                edgeExists = true;
                                break;
                            }
                        }
                        if (edgeExists) continue;
                        node.createRelationshipTo(n, EdgeType.ChildOf);
                    }
                }
            }
        }
    }

    public void addChemblTarget(Target t, String targetClass) {
        Node node = addNodeIfAbsent(t, "node.chembl.TargetID", "TargetID", t.id);
        Index<Node> index = gdb.index().forNodes("node.chembl.TargetID");
        try (IndexHits<Node> hits = index.get("TargetID", targetClass)) {
            for (Node n : hits) {
                node.createRelationshipTo(n, EdgeType.IsA);
            }
        }
    }

    public void loadFromDb(ResultSet rset) throws SQLException {
        while (rset.next()) {
            String[] tclasses = new String[8];
            for (int i = 1; i <= 8; i++) {
                tclasses[i - 1] = rset.getString("l" + i);
            }
            addChemblTargetClasses(tclasses);

            // Add in the actual target, and link to most specific target class
            String targetClass = null;
            for (int i = 1; i < 8; i++) {
                if (tclasses[i] == null) {
                    targetClass = tclasses[i - 1];
                    break;
                }
            }
            Target t = new Target();
            t.id = rset.getString("tid");
            t.acc = rset.getString("accession");
            t.name = rset.getString("pref_name");
            t.type = TargetType.MolecularTarget;
            t.taxId = 9606;
            addChemblTarget(t, targetClass);
        }
    }

    public static void main(String[] args) throws SQLException {
        if (args.length < 1) {
            System.err.println("Usage: " + ChemblTargetLoader.class.getName() + " DBDIR ");
            System.exit(-1);
        }

        String jdbcUrl = System.getProperty("jdbc.url");
        if (jdbcUrl == null || jdbcUrl.trim().equals("")) {
            System.out.println("Must specify JDBC URL to ChEMBL MySQL instance in the form: -Djdbc.url=jdbc:mysql://host/DBNAME?user=USER&password=PASS");
            System.exit(-1);
        }

        Connection conn = DriverManager.getConnection(jdbcUrl);
        PreparedStatement pst = conn.prepareStatement("SELECT  " +
                "    td.tid, td.pref_name, description, accession, pfc . * " +
                "FROM " +
                "    target_dictionary td, " +
                "    target_components tc, " +
                "    component_sequences cs, " +
                "    component_class cc, " +
                "    protein_family_classification pfc " +
                "WHERE " +
                "td.tax_id = 9606 AND  " +
                "td.tid = tc.tid " +
                "        AND tc.component_id = cs.component_id " +
                "        AND cc.component_id = cs.component_id " +
                "        AND pfc.protein_class_id = cc.protein_class_id ");
        ResultSet rset = pst.executeQuery();

        File dbDir = new File(args[0]);
        if (dbDir.exists()) {
            System.out.println(dbDir+" already exists. Won't overwrite, so exiting");
            System.exit(-1);
        }

        GraphDatabaseBuilder builder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dbDir);
        GraphDatabaseService gdb = builder.newGraphDatabase();
        try (Transaction tx = gdb.beginTx()) {
            ChemblTargetLoader loader = new ChemblTargetLoader(gdb);
            loader.loadFromDb(rset);
            tx.success();
        } finally {
            gdb.shutdown();
            pst.close();
            rset.close();
            conn.close();
        }
    }
}
