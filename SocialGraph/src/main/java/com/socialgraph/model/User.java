package com.socialgraph.model;

public class User {
    private final String username;
    private String name;
    private String email;
    private String passwordHash;
    private String bio;

    public User(String username, String name, String email,
                String passwordHash, String bio) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.bio = bio;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getBio() {
        return bio;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
