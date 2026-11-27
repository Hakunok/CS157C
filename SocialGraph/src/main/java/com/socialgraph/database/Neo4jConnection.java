package com.socialgraph.database;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class Neo4jConnection implements AutoCloseable {

    private final Driver driver;

    public Neo4jConnection() {
        String uri = System.getenv().getOrDefault(
            "NEO4J_URI", "bolt://localhost:7687"
        );

        String username = System.getenv().getOrDefault(
            "NEO4J_USERNAME", "neo4j"
        );

        String password = System.getenv("NEO4J_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "NEO4J_PASSWORD environment variable is required."
            );
        }

        driver = GraphDatabase.driver(
            uri,
            AuthTokens.basic(username, password)
        );

        try {
            driver.verifyConnectivity();
        } catch (RuntimeException ex) {
            driver.close();
            throw ex;
        }
    }

    public Driver getDriver() {
        return driver;
    }

    @Override
    public void close() {
        driver.close();
    }
}
