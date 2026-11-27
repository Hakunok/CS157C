package com.socialgraph.database;

import org.neo4j.driver.Session;

public class ConnectionTest {

    public static void main(String[] args) {
        try (Neo4jConnection connection = new Neo4jConnection();
             Session session = connection.getDriver().session()) {

            String message = session.run(
                "RETURN 'Connected to Neo4j successfully' AS message"
            ).single().get("message").asString();

            System.out.println(message);

        } catch (Exception ex) {
            System.err.println(
                "Neo4j connection failed: " + ex.getMessage()
            );
            System.exit(1);
        }
    }
}
