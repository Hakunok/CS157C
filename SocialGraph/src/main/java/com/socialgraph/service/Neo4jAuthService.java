package com.socialgraph.service;

import com.socialgraph.model.User;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Neo4jAuthService {

    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private final Driver driver;
    private final SecureRandom random = new SecureRandom();

    public Neo4jAuthService(Driver driver) {
        this.driver = driver;
    }

    private String normalize(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private User toUser(Record record) {
        return new User(
            record.get("username").asString(),
            record.get("name").asString(),
            record.get("email").asString(),
            record.get("passwordHash").asString(""),
            record.get("bio").asString("")
        );
    }

    private static final String USER_FIELDS =
        "u.username AS username, " +
        "coalesce(u.name, u.username) AS name, " +
        "coalesce(u.email, '') AS email, " +
        "coalesce(u.passwordHash, '') AS passwordHash, " +
        "coalesce(u.bio, '') AS bio";

    public User register(
            String name,
            String username,
            String email,
            String password,
            String bio) {

        if (name == null || email == null || password == null) {
            throw new IllegalArgumentException(
                "Name, username, email, and password are required."
            );
        }

        name = name.trim();
        username = normalize(username);
        email = email.trim();
        bio = bio == null ? "" : bio.trim();

        if (name.isBlank() || username.isBlank()
                || email.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException(
                "Name, username, email, and password are required."
            );
        }

        if (!username.matches("[a-z0-9_]{3,30}")) {
            throw new IllegalArgumentException(
                "Username must contain 3-30 letters, numbers, or underscores."
            );
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                "Enter a valid email address."
            );
        }

        if (password.length() < 8) {
            throw new IllegalArgumentException(
                "Password must contain at least 8 characters."
            );
        }

        String hash = hashPassword(password);

        Map<String, Object> params = Map.of(
            "username", username,
            "name", name,
            "email", email,
            "passwordHash", hash,
            "bio", bio
        );

        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                boolean exists = tx.run(
                    "MATCH (u:User {username: $username}) " +
                    "RETURN count(u) > 0 AS exists",
                    params
                ).single().get("exists").asBoolean();

                if (exists) {
                    throw new IllegalArgumentException(
                        "This username is already registered."
                    );
                }

                Record record = tx.run(
                    "CREATE (u:User { " +
                    "username: $username, " +
                    "name: $name, " +
                    "email: $email, " +
                    "passwordHash: $passwordHash, " +
                    "bio: $bio, " +
                    "createdAt: datetime() " +
                    "}) " +
                    "RETURN " + USER_FIELDS,
                    params
                ).single();

                return toUser(record);
            });
        } catch (ClientException ex) {
            if (ex.code().contains("ConstraintValidationFailed")) {
                throw new IllegalArgumentException(
                    "This username is already registered."
                );
            }
            throw ex;
        }
    }

    public User login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException(
                "Invalid username or password."
            );
        }

        User user = findUser(username);

        if (user == null || user.getPasswordHash().isBlank()
                || !verifyPassword(password, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                "Invalid username or password."
            );
        }

        return user;
    }

    public User findUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var result = tx.run(
                    "MATCH (u:User {username: $username}) " +
                    "RETURN " + USER_FIELDS,
                    Map.of("username", normalize(username))
                );

                return result.hasNext()
                    ? toUser(result.next())
                    : null;
            });
        }
    }

    public List<User> getAllUsers() {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                List<User> users = new ArrayList<>();

                var result = tx.run(
                    "MATCH (u:User) " +
                    "WHERE u.username IS NOT NULL " +
                    "RETURN " + USER_FIELDS + " " +
                    "ORDER BY username"
                );

                while (result.hasNext()) {
                    users.add(toUser(result.next()));
                }

                return users;
            });
        }
    }

    public User updateProfile(
            String username,
            String name,
            String email,
            String bio) {

        if (name == null || email == null) {
            throw new IllegalArgumentException(
                "Name and email are required."
            );
        }

        name = name.trim();
        email = email.trim();
        bio = bio == null ? "" : bio.trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                "Name cannot be empty."
            );
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                "Enter a valid email address."
            );
        }

        Map<String, Object> params = Map.of(
            "username", normalize(username),
            "name", name,
            "email", email,
            "bio", bio
        );

        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                var result = tx.run(
                    "MATCH (u:User {username: $username}) " +
                    "SET u.name = $name, " +
                    "u.email = $email, " +
                    "u.bio = $bio " +
                    "RETURN " + USER_FIELDS,
                    params
                );

                if (!result.hasNext()) {
                    throw new IllegalArgumentException(
                        "User not found."
                    );
                }

                return toUser(result.next());
            });
        }
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        byte[] hash = deriveKey(password, salt, ITERATIONS);

        return ITERATIONS + ":" +
            Base64.getEncoder().encodeToString(salt) + ":" +
            Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split(":");

            if (parts.length != 3) {
                return false;
            }

            int iterations = Integer.parseInt(parts[0]);

            if (iterations < 1 || iterations > 1000000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);

            if (salt.length != SALT_LENGTH ||
                    expected.length != KEY_LENGTH / 8) {
                return false;
            }

            byte[] actual = deriveKey(
                password, salt, iterations
            );

            return MessageDigest.isEqual(expected, actual);

        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] deriveKey(
            String password,
            byte[] salt,
            int iterations) {

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            iterations,
            KEY_LENGTH
        );

        try {
            SecretKeyFactory factory =
                SecretKeyFactory.getInstance(
                    "PBKDF2WithHmacSHA256"
                );

            return factory.generateSecret(spec).getEncoded();

        } catch (Exception ex) {
            throw new IllegalStateException(
                "Password hashing failed.", ex
            );

        } finally {
            spec.clearPassword();
        }
    }
}
