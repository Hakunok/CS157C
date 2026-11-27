package com.socialgraph.service;

import com.socialgraph.model.User;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserSearchService {

    public record UserSearchResult(
        User user,
        boolean following
    ) {}

    private final Driver driver;

    public UserSearchService(Driver driver) {
        this.driver = driver;
    }

    private String normalize(String value) {
        return value == null
            ? ""
            : value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parameters(
            String searchText,
            int page,
            int pageSize) {

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));

        return Map.of(
            "search", normalize(searchText),
            "skip", (long) safePage * safeSize,
            "limit", safeSize
        );
    }

    /**
     * Returns a page of users without follow information.
     * Retained for compatibility with existing frontend code.
     */
    public List<User> searchUsers(
            String searchText,
            int page,
            int pageSize) {

        Map<String, Object> params =
            parameters(searchText, page, pageSize);

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "AND (" +
                    "toLower(coalesce(u.name, '')) CONTAINS $search " +
                    "OR toLower(u.username) CONTAINS $search" +
                    ") " +
                    "RETURN u.username AS username, " +
                    "coalesce(u.name, u.username) AS name, " +
                    "coalesce(u.email, '') AS email, " +
                    "coalesce(u.bio, '') AS bio " +
                    "ORDER BY username " +
                    "SKIP $skip LIMIT $limit",
                    params
                );

                List<User> users = new ArrayList<>();

                while (result.hasNext()) {
                    var row = result.next();

                    users.add(new User(
                        row.get("username").asString(),
                        row.get("name").asString(),
                        row.get("email").asString(),
                        "",
                        row.get("bio").asString()
                    ));
                }

                return users;
            });
        }
    }

    /**
     * Retrieves user profiles and follow statuses
     * in one Neo4j query.
     */
    public List<UserSearchResult> searchWithFollowStatus(
            String currentUsername,
            String searchText,
            int page,
            int pageSize) {

        if (currentUsername == null ||
                currentUsername.isBlank()) {
            throw new IllegalArgumentException(
                "Current username is required."
            );
        }

        Map<String, Object> paging =
            parameters(searchText, page, pageSize);

        Map<String, Object> params = Map.of(
            "currentUsername", normalize(currentUsername),
            "search", paging.get("search"),
            "skip", paging.get("skip"),
            "limit", paging.get("limit")
        );

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "AND (" +
                    "toLower(coalesce(u.name, '')) CONTAINS $search " +
                    "OR toLower(u.username) CONTAINS $search" +
                    ") " +
                    "WITH u " +
                    "ORDER BY u.username " +
                    "SKIP $skip LIMIT $limit " +
                    "OPTIONAL MATCH (me:User " +
                    "{username: $currentUsername}) " +
                    "RETURN u.username AS username, " +
                    "coalesce(u.name, u.username) AS name, " +
                    "coalesce(u.email, '') AS email, " +
                    "coalesce(u.bio, '') AS bio, " +
                    "EXISTS { " +
                    "MATCH (me)-[:FOLLOWS]->(u) " +
                    "} AS following " +
                    "ORDER BY username",
                    params
                );

                List<UserSearchResult> users =
                    new ArrayList<>();

                while (result.hasNext()) {
                    var row = result.next();

                    User user = new User(
                        row.get("username").asString(),
                        row.get("name").asString(),
                        row.get("email").asString(),
                        "",
                        row.get("bio").asString()
                    );

                    users.add(new UserSearchResult(
                        user,
                        row.get("following").asBoolean()
                    ));
                }

                return users;
            });
        }
    }

    /**
     * Counts users matching a full or partial
     * name or username.
     */
    public long countUsers(String searchText) {
        String query = normalize(searchText);

        try (Session session = driver.session()) {
            return session.executeRead(tx ->
                tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "AND (" +
                    "toLower(coalesce(u.name, '')) CONTAINS $search " +
                    "OR toLower(u.username) CONTAINS $search" +
                    ") " +
                    "RETURN count(u) AS total",
                    Map.of("search", query)
                ).single().get("total").asLong()
            );
        }
    }
}
