package com.socialgraph.service;

import com.socialgraph.model.User;
import java.util.List;

/**
 * Contract between the JavaFX frontend and the social graph backend.
 *
 * The frontend must not depend on how graph data is stored.
 * Implementations may use temporary in-memory data or Neo4j.
 */
public interface SocialGraphService {

    // UC-10: View all users.
    List<User> getAllUsers();

    // UC-5: Follow another user.
    void follow(String sourceUsername, String targetUsername);

    // UC-6: Unfollow a user.
    void unfollow(String sourceUsername, String targetUsername);

    // Check whether a directed follow relationship exists.
    boolean isFollowing(String sourceUsername, String targetUsername);

    // UC-7: Following and followers.
    List<User> getFollowing(String username);

    List<User> getFollowers(String username);

    // UC-8: Users followed by both specified users.
    List<User> getMutualConnections(
        String firstUsername,
        String secondUsername
    );

    // UC-9: Exactly two-hop recommendations.
    List<User> getRecommendations(String username);

    // UC-12: Popular users ranked by follower count.
    List<User> getPopularUsers();

    int getFollowerCount(String username);

    // UC-13: Shortest directed follow path, at most 4 hops.
    // Returns an empty list when no qualifying path exists.
    List<User> findShortestPath(
        String sourceUsername,
        String targetUsername
    );
}
