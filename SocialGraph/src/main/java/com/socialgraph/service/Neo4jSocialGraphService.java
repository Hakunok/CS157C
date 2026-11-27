package com.socialgraph.service;

import com.socialgraph.model.User;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Neo4jSocialGraphService implements SocialGraphService {

    private final Driver driver;

    public Neo4jSocialGraphService(Driver driver) {
        this.driver = driver;
    }

    private String key(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private User toUser(Record record) {
        return new User(
            record.get("username").asString(),
            record.get("name").asString(),
            record.get("email").asString(),
            "",
            record.get("bio").asString()
        );
    }

    private List<User> readUsers(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                List<User> users = new ArrayList<>();
                var result = tx.run(query, params);

                while (result.hasNext()) {
                    users.add(toUser(result.next()));
                }

                return users;
            });
        }
    }

    private static final String USER_FIELDS =
        "u.username AS username, " +
        "coalesce(u.name, u.username) AS name, " +
        "coalesce(u.email, '') AS email, " +
        "coalesce(u.bio, '') AS bio";

    @Override
    public List<User> getAllUsers() {
        return readUsers(
            "MATCH (u:User) " +
            "WHERE u.username IS NOT NULL " +
            "RETURN " + USER_FIELDS + " " +
            "ORDER BY username",
            Map.of()
        );
    }

    @Override
    public void follow(String sourceUsername, String targetUsername) {
        String source = key(sourceUsername);
        String target = key(targetUsername);

        if (source.equals(target)) {
            throw new IllegalArgumentException(
                "You cannot follow yourself."
            );
        }

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                var result = tx.run(
                    "OPTIONAL MATCH (a:User {username: $source}) " +
                    "OPTIONAL MATCH (b:User {username: $target}) " +
                    "RETURN a IS NOT NULL AS sourceExists, " +
                    "b IS NOT NULL AS targetExists",
                    Map.of("source", source, "target", target)
                ).single();

                if (!result.get("sourceExists").asBoolean()
                        || !result.get("targetExists").asBoolean()) {
                    throw new IllegalArgumentException("User not found.");
                }

                boolean exists = tx.run(
                    "MATCH (a:User {username: $source})" +
                    "-[r:FOLLOWS]->" +
                    "(b:User {username: $target}) " +
                    "RETURN count(r) > 0 AS exists",
                    Map.of("source", source, "target", target)
                ).single().get("exists").asBoolean();

                if (exists) {
                    throw new IllegalArgumentException(
                        "You already follow this user."
                    );
                }

                tx.run(
                    "MATCH (a:User {username: $source}), " +
                    "(b:User {username: $target}) " +
                    "MERGE (a)-[:FOLLOWS]->(b)",
                    Map.of("source", source, "target", target)
                ).consume();

                return null;
            });
        }
    }

    @Override
    public void unfollow(String sourceUsername, String targetUsername) {
        try (Session session = driver.session()) {
            int deleted = session.executeWrite(tx -> {
                var summary = tx.run(
                    "MATCH (a:User {username: $source})" +
                    "-[r:FOLLOWS]->" +
                    "(b:User {username: $target}) " +
                    "DELETE r",
                    Map.of(
                        "source", key(sourceUsername),
                        "target", key(targetUsername)
                    )
                ).consume();

                return summary.counters().relationshipsDeleted();
            });

            if (deleted == 0) {
                throw new IllegalArgumentException(
                    "You are not following this user."
                );
            }
        }
    }

    @Override
    public boolean isFollowing(String sourceUsername, String targetUsername) {
        try (Session session = driver.session()) {
            return session.executeRead(tx ->
                tx.run(
                    "MATCH (a:User {username: $source}) " +
                    "RETURN EXISTS { " +
                    "MATCH (a)-[:FOLLOWS]->" +
                    "(:User {username: $target}) " +
                    "} AS follows",
                    Map.of(
                        "source", key(sourceUsername),
                        "target", key(targetUsername)
                    )
                ).stream()
                 .findFirst()
                 .map(r -> r.get("follows").asBoolean())
                 .orElse(false)
            );
        }
    }

    @Override
    public List<User> getFollowing(String username) {
        return readUsers(
            "MATCH (:User {username: $username})" +
            "-[:FOLLOWS]->(u:User) " +
            "RETURN " + USER_FIELDS + " " +
            "ORDER BY username",
            Map.of("username", key(username))
        );
    }

    @Override
    public List<User> getFollowers(String username) {
        return readUsers(
            "MATCH (u:User)-[:FOLLOWS]->" +
            "(:User {username: $username}) " +
            "RETURN " + USER_FIELDS + " " +
            "ORDER BY username",
            Map.of("username", key(username))
        );
    }

    @Override
    public List<User> getMutualConnections(
            String firstUsername, String secondUsername) {

        return readUsers(
            "MATCH (:User {username: $first})" +
            "-[:FOLLOWS]->(u:User)<-[:FOLLOWS]-" +
            "(:User {username: $second}) " +
            "RETURN DISTINCT " + USER_FIELDS + " " +
            "ORDER BY username",
            Map.of(
                "first", key(firstUsername),
                "second", key(secondUsername)
            )
        );
    }

    @Override
    public List<User> getRecommendations(String username) {
        return readUsers(
            "MATCH (me:User {username: $username})" +
            "-[:FOLLOWS]->(:User)" +
            "-[:FOLLOWS]->(u:User) " +
            "WHERE u <> me " +
            "AND NOT EXISTS { " +
            "MATCH (me)-[:FOLLOWS]->(u) " +
            "} " +
            "RETURN DISTINCT " + USER_FIELDS + " " +
            "ORDER BY username",
            Map.of("username", key(username))
        );
    }

    @Override
    public int getFollowerCount(String username) {
        try (Session session = driver.session()) {
            return session.executeRead(tx ->
                tx.run(
                    "MATCH (u:User {username: $username}) " +
                    "OPTIONAL MATCH (:User)-[r:FOLLOWS]->(u) " +
                    "RETURN count(r) AS followers",
                    Map.of("username", key(username))
                ).stream()
                 .findFirst()
                 .map(r -> r.get("followers").asInt())
                 .orElse(0)
            );
        }
    }

    @Override
    public List<User> getPopularUsers() {
        return readUsers(
            "MATCH (u:User) " +
            "WHERE u.username IS NOT NULL " +
            "OPTIONAL MATCH (:User)-[r:FOLLOWS]->(u) " +
            "WITH u, count(r) AS followerCount " +
            "RETURN " + USER_FIELDS + " " +
            "ORDER BY followerCount DESC, username",
            Map.of()
        );
    }

    @Override
    public List<User> findShortestPath(
            String sourceUsername, String targetUsername) {

        String source = key(sourceUsername);
        String target = key(targetUsername);

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (a:User {username: $source}), " +
                    "(b:User {username: $target}) " +
                    "MATCH p = shortestPath(" +
                    "(a)-[:FOLLOWS*..4]->(b)) " +
                    "RETURN [n IN nodes(p) | " +
                    "{username: n.username, " +
                    "name: coalesce(n.name, n.username), " +
                    "email: coalesce(n.email, ''), " +
                    "bio: coalesce(n.bio, '')}] AS path",
                    Map.of("source", source, "target", target)
                );

                if (!result.hasNext()) {
                    return List.of();
                }

                var nodes = result.single().get("path").asList();
                List<User> path = new ArrayList<>();

                for (Object item : nodes) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> node =
                        (Map<String, Object>) item;

                    path.add(new User(
                        (String) node.get("username"),
                        (String) node.get("name"),
                        (String) node.get("email"),
                        "",
                        (String) node.get("bio")
                    ));
                }

                return path;
            });
        }
    }
}
