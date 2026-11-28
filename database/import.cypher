// SocialGraph import: constraints, users, follows.
// Safe to run more than once: IF NOT EXISTS and MERGE skip work already done.
// Needs users_profiles.csv and follows.csv in the container's /import folder
// (see database/README.md).

// 1. Constraints. Each one also creates an index, so the MATCH lookups below
//    jump straight to a user instead of scanning all 81k of them.
CREATE CONSTRAINT user_userId IF NOT EXISTS
FOR (u:User) REQUIRE u.userId IS UNIQUE;

CREATE CONSTRAINT user_username IF NOT EXISTS
FOR (u:User) REQUIRE u.username IS UNIQUE;

// 2. Users, committed in batches of 10,000 rows.
LOAD CSV WITH HEADERS FROM 'file:///users_profiles.csv' AS row
CALL (row) {
  MERGE (u:User {userId: row.userId})
  SET u.username     = row.username,
      u.name         = row.name,
      u.email        = row.email,
      u.bio          = coalesce(row.bio, ''),
      u.passwordHash = coalesce(row.passwordHash, ''),
      u.source       = 'dataset'
} IN TRANSACTIONS OF 10000 ROWS;

// 3. Follows: source follows target.
LOAD CSV WITH HEADERS FROM 'file:///follows.csv' AS row
CALL (row) {
  MATCH (a:User {userId: row.source})
  MATCH (b:User {userId: row.target})
  MERGE (a)-[:FOLLOWS]->(b)
} IN TRANSACTIONS OF 10000 ROWS;

// 4. Check the counts. Expect 81306 users and 1768135 follows.
MATCH (u:User) RETURN count(u) AS users;
MATCH ()-[r:FOLLOWS]->() RETURN count(r) AS follows;
