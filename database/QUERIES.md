# Primary Cypher Queries per Use Case

For the report. These are taken from the service classes in `SocialGraph/src/main/java/com/socialgraph/service/`. `$name` values are query parameters filled in by the Java code. Use-case numbers follow the comments in `SocialGraphService.java`.

> **TODO:** match the numbering to the official list of 13 use cases. UC-1 to UC-4 and UC-11 are not numbered in the code, and "network visualization" has no page yet.

## Setup (database/import.cypher)

```cypher
CREATE CONSTRAINT user_userId IF NOT EXISTS FOR (u:User) REQUIRE u.userId IS UNIQUE;
CREATE CONSTRAINT user_username IF NOT EXISTS FOR (u:User) REQUIRE u.username IS UNIQUE;

LOAD CSV WITH HEADERS FROM 'file:///users_profiles.csv' AS row
CALL (row) {
  MERGE (u:User {userId: row.userId})
  SET u.username = row.username, u.name = row.name, u.email = row.email,
      u.bio = coalesce(row.bio, ''), u.passwordHash = coalesce(row.passwordHash, ''),
      u.source = 'dataset'
} IN TRANSACTIONS OF 10000 ROWS;

LOAD CSV WITH HEADERS FROM 'file:///follows.csv' AS row
CALL (row) {
  MATCH (a:User {userId: row.source})
  MATCH (b:User {userId: row.target})
  MERGE (a)-[:FOLLOWS]->(b)
} IN TRANSACTIONS OF 10000 ROWS;
```

## Registration (Neo4jAuthService.register)

```cypher
CREATE (u:User {username: $username, name: $name, email: $email,
                passwordHash: $passwordHash, bio: $bio, createdAt: datetime()})
```
The `user_username` constraint rejects duplicate usernames.

## Login and view profile (Neo4jAuthService.findUser)

```cypher
MATCH (u:User {username: $username})
RETURN u.username, u.name, u.email, u.passwordHash, u.bio
```
The password check (PBKDF2) runs in Java, and the hash is never shown in the UI.

## Update profile (Neo4jAuthService.updateProfile)

```cypher
MATCH (u:User {username: $username})
SET u.name = $name, u.email = $email, u.bio = $bio
```

## UC-5 Follow (Neo4jSocialGraphService.follow)

```cypher
MATCH (a:User {username: $source}), (b:User {username: $target})
MERGE (a)-[:FOLLOWS]->(b)
```
Java rejects self-follows. A check query rejects duplicate follows first.

## UC-6 Unfollow

```cypher
MATCH (a:User {username: $source})-[r:FOLLOWS]->(b:User {username: $target})
DELETE r
```

## UC-7 Following and followers

```cypher
MATCH (:User {username: $username})-[:FOLLOWS]->(u:User) RETURN u ORDER BY u.username;
MATCH (u:User)-[:FOLLOWS]->(:User {username: $username}) RETURN u ORDER BY u.username;
```

## UC-8 Mutual connections

```cypher
MATCH (:User {username: $first})-[:FOLLOWS]->(u:User)<-[:FOLLOWS]-(:User {username: $second})
RETURN DISTINCT u ORDER BY u.username
```

## UC-9 Recommendations (exactly two hops)

```cypher
MATCH (me:User {username: $username})-[:FOLLOWS]->(:User)-[:FOLLOWS]->(u:User)
WHERE u <> me AND NOT EXISTS { MATCH (me)-[:FOLLOWS]->(u) }
RETURN DISTINCT u ORDER BY u.username
```

## UC-10 View and search users (UserSearchService)

```cypher
MATCH (u:User) WHERE u.username IS NOT NULL RETURN u ORDER BY u.username
```
**TODO:** add the search query from `UserSearchService.searchUsers`.

## UC-12 Popular users (PopularUsersService, paginated)

```cypher
MATCH (u:User) WHERE u.username IS NOT NULL
OPTIONAL MATCH (:User)-[r:FOLLOWS]->(u)
WITH u, count(r) AS followerCount
ORDER BY followerCount DESC, u.username
SKIP $skip LIMIT $limit
RETURN u, followerCount
```

## UC-13 Degrees of separation (shortest path, at most 4 hops)

```cypher
MATCH (a:User {username: $source}), (b:User {username: $target})
MATCH p = shortestPath((a)-[:FOLLOWS*..4]->(b))
RETURN [n IN nodes(p) | n.username] AS path
```
If there is no path within 4 hops, the query returns no rows and the app reports "no path".

## Verification

```cypher
MATCH (u:User) RETURN count(u);            // 81,306 plus registered users
MATCH ()-[r:FOLLOWS]->() RETURN count(r);  // 1,768,135 plus follows made in the app
```
