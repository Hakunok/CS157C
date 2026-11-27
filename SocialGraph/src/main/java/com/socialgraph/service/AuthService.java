
package com.socialgraph.service;

import com.socialgraph.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuthService {

    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;

    private final Map<String, User> users = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public User register(
            String name,
            String username,
            String email,
            String password,
            String bio) {

        if (name == null || username == null ||
                email == null || password == null) {
            throw new IllegalArgumentException(
                "Name, username, email, and password are required."
            );
        }

        name = name.trim();
        username = username.trim();
        email = email.trim();
        bio = bio == null ? "" : bio.trim();

        if (name.isBlank() || username.isBlank()
                || email.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException(
                "Name, username, email, and password are required."
            );
        }

        if (!username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException(
                "Username must be 3-30 characters using letters, numbers, or underscores."
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

        String key = username.toLowerCase(Locale.ROOT);

        if (users.containsKey(key)) {
            throw new IllegalArgumentException(
                "This username is already registered."
            );
        }

        User user = new User(
            username,
            name,
            email,
            hashPassword(password),
            bio
        );

        users.put(key, user);
        return user;
    }

    public User login(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException(
                "Invalid username or password."
            );
        }

        User user = findUser(username);

        if (user == null ||
                !verifyPassword(
                    password,
                    user.getPasswordHash()
                )) {
            throw new IllegalArgumentException(
                "Invalid username or password."
            );
        }

        return user;
    }

    public User findUser(String username) {
        if (username == null) {
            return null;
        }

        return users.get(
            username.trim().toLowerCase(Locale.ROOT)
        );
    }

    public List<User> getAllUsers() {
        List<User> result = new ArrayList<>(users.values());

        result.sort(
            Comparator.comparing(
                User::getUsername,
                String.CASE_INSENSITIVE_ORDER
            )
        );

        return result;
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        byte[] hash = deriveKey(
            password,
            salt,
            ITERATIONS
        );

        return ITERATIONS + ":"
            + Base64.getEncoder().encodeToString(salt) + ":"
            + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(
            String password,
            String stored) {

        try {
            String[] parts = stored.split(":");

            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);

            byte[] actual = deriveKey(
                password,
                salt,
                iterations
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
                "Password hashing failed.",
                ex
            );

        } finally {
            spec.clearPassword();
        }
    }
}
