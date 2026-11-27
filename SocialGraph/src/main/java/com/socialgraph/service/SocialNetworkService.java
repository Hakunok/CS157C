package com.socialgraph.service;

import com.socialgraph.model.User;
import java.util.*;

public class SocialNetworkService implements SocialGraphService {

    private final AuthService auth;
    private final Map<String, Set<String>> following = new HashMap<>();

    public SocialNetworkService(AuthService auth) {
        this.auth = auth;
    }

    private String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private User requireUser(String username) {
        User user = username == null ? null : auth.findUser(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        return user;
    }

    @Override
    public List<User> getAllUsers() {
        return auth.getAllUsers();
    }

    @Override
    public void follow(String sourceName, String targetName) {
        String source = key(requireUser(sourceName).getUsername());
        String target = key(requireUser(targetName).getUsername());

        if (source.equals(target)) {
            throw new IllegalArgumentException(
                "You cannot follow yourself."
            );
        }

        Set<String> targets = following.computeIfAbsent(
            source, ignored -> new HashSet<>()
        );

        if (!targets.add(target)) {
            throw new IllegalArgumentException(
                "You already follow this user."
            );
        }
    }

    @Override
    public void unfollow(String sourceName, String targetName) {
        String source = key(requireUser(sourceName).getUsername());
        String target = key(requireUser(targetName).getUsername());

        Set<String> targets = following.get(source);

        if (targets == null || !targets.remove(target)) {
            throw new IllegalArgumentException(
                "You are not following this user."
            );
        }
    }

    @Override
    public boolean isFollowing(String source, String target) {
        if (source == null || target == null) {
            return false;
        }

        return following.getOrDefault(
            key(source), Collections.emptySet()
        ).contains(key(target));
    }

    private List<User> resolveUsers(Collection<String> names) {
        List<User> result = new ArrayList<>();

        for (String name : names) {
            User user = auth.findUser(name);
            if (user != null) {
                result.add(user);
            }
        }

        result.sort(Comparator.comparing(
            User::getUsername,
            String.CASE_INSENSITIVE_ORDER
        ));

        return result;
    }

    @Override
    public List<User> getFollowing(String username) {
        String source = key(requireUser(username).getUsername());

        return resolveUsers(following.getOrDefault(
            source, Collections.emptySet()
        ));
    }

    @Override
    public List<User> getFollowers(String username) {
        String target = key(requireUser(username).getUsername());
        Set<String> followers = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry
                : following.entrySet()) {
            if (entry.getValue().contains(target)) {
                followers.add(entry.getKey());
            }
        }

        return resolveUsers(followers);
    }

    @Override
    public List<User> getMutualConnections(String first, String second) {
        String a = key(requireUser(first).getUsername());
        String b = key(requireUser(second).getUsername());

        Set<String> shared = new HashSet<>(
            following.getOrDefault(a, Collections.emptySet())
        );

        shared.retainAll(
            following.getOrDefault(b, Collections.emptySet())
        );

        return resolveUsers(shared);
    }

    @Override
    public List<User> getRecommendations(String username) {
        String source = key(requireUser(username).getUsername());

        Set<String> direct = following.getOrDefault(
            source, Collections.emptySet()
        );

        Set<String> recommended = new HashSet<>();

        for (String middle : direct) {
            for (String candidate : following.getOrDefault(
                    middle, Collections.emptySet())) {

                if (!candidate.equals(source)
                        && !direct.contains(candidate)) {
                    recommended.add(candidate);
                }
            }
        }

        return resolveUsers(recommended);
    }

    @Override
    public int getFollowerCount(String username) {
        String target = key(requireUser(username).getUsername());
        int count = 0;

        for (Set<String> targets : following.values()) {
            if (targets.contains(target)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public List<User> getPopularUsers() {
        List<User> users = new ArrayList<>(getAllUsers());

        users.sort(
            Comparator.comparingInt(
                (User user) -> getFollowerCount(user.getUsername())
            ).reversed().thenComparing(
                User::getUsername,
                String.CASE_INSENSITIVE_ORDER
            )
        );

        return users;
    }

    @Override
    public List<User> findShortestPath(String fromName, String toName) {
        String source = key(requireUser(fromName).getUsername());
        String target = key(requireUser(toName).getUsername());

        Queue<List<String>> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(List.of(source));
        visited.add(source);

        while (!queue.isEmpty()) {
            List<String> path = queue.remove();
            String last = path.get(path.size() - 1);

            if (last.equals(target)) {
                List<User> result = new ArrayList<>();
                for (String name : path) {
                    result.add(requireUser(name));
                }
                return result;
            }

            if (path.size() - 1 >= 4) {
                continue;
            }

            for (String next : following.getOrDefault(
                    last, Collections.emptySet())) {

                if (visited.add(next)) {
                    List<String> extended = new ArrayList<>(path);
                    extended.add(next);
                    queue.add(extended);
                }
            }
        }

        return Collections.emptyList();
    }
}
