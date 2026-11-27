package com.socialgraph.service;

import com.socialgraph.model.User;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PopularUsersService {

    public record PopularUser(User user, long followers) {}

    private final Driver driver;

    public PopularUsersService(Driver driver) {
        this.driver = driver;
    }

    public List<PopularUser> getPopularUsers(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        long skip = (long) safePage * safeSize;

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "OPTIONAL MATCH (:User)-[r:FOLLOWS]->(u) " +
                    "WITH u, count(r) AS followerCount " +
                    "ORDER BY followerCount DESC, u.username " +
                    "SKIP $skip LIMIT $limit " +
                    "RETURN u.username AS username, " +
                    "coalesce(u.name, u.username) AS name, " +
                    "coalesce(u.email, '') AS email, " +
                    "coalesce(u.bio, '') AS bio, " +
                    "followerCount",
                    Map.of("skip", skip, "limit", safeSize)
                );

                List<PopularUser> users = new ArrayList<>();

                while (result.hasNext()) {
                    var row = result.next();

                    User user = new User(
                        row.get("username").asString(),
                        row.get("name").asString(),
                        row.get("email").asString(),
                        "",
                        row.get("bio").asString()
                    );

                    users.add(new PopularUser(
                        user,
                        row.get("followerCount").asLong()
                    ));
                }

                return users;
            });
        }
    }

    public long countUsers() {
        try (Session session = driver.session()) {
            return session.executeRead(tx ->
                tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "RETURN count(u) AS total"
                ).single().get("total").asLong()
            );
        }
    }
}
