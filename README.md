## Experiments with Neo4J

Various experiments with [Neo4j](http://neo4j.com/). Currently the code simply loads in the [ChEMBL](https://www.ebi.ac.uk/chembl/)
target hierarchy and as well as the actual targets as a graph. It employs an embedded database which can be explored using the
Neo4j application.

### Build

The project is based on [Maven](https://maven.apache.org/), so simply doing
```
mvn package
```
should generate an executable JAR file called `target/neo4j-ctl-1.0-SNAPSHOT.jar`. Currently no tests are implemented.

### Run

The code expects that you have [ChEMBL](https://www.ebi.ac.uk/chembl/) installed as a MySQL instance (and has been tested
with a ChEMBL 20 instance). You'll need to specify
a name for the graph database directory - if it doesn't exist it will be created. If it does exist, the code will not
overwrite and exit. In addition you'll need to specify the JDBC URL for the database connection. An example is
```
java -Djdbc.url="jdbc:mysql://host.name/chembl_20?user=USER&password=PASS"  -jar target/neo4j-ctl-1.0-SNAPSHOT.jar graph.db
```

You can then start up the Neo4j application and explore the graph db using the [Cypher](http://neo4j.com/developer/cypher-query-language/)
query language.