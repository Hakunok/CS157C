# SocialGraph Backend Integration

## Technology
- Frontend: Java 23 and JavaFX
- Database: Neo4j
- Build system: Maven
- Graph relationship: (:User)-[:FOLLOWS]->(:User)

## User properties
- userId: unique identifier
- username: unique, immutable
- name: required
- email: required
- passwordHash: authentication credential for registered accounts
- bio: optional

The imported Twitter dataset may provide only user identifiers
and directed follow relationships.

Synthetic profile information must be documented separately
from the original public dataset.

Imported accounts must not share a known default password.

## Frontend service contract

Implement SocialGraphService using Neo4j.

Required methods:
- getAllUsers()
- follow()
- unfollow()
- isFollowing()
- getFollowing()
- getFollowers()
- getMutualConnections()
- getRecommendations()
- getPopularUsers()
- getFollowerCount()
- findShortestPath()

## Authentication

The current AuthService uses temporary in-memory storage.

The final backend must provide persistent:
- Registration with unique usernames
- Login and password verification
- Profile retrieval
- Profile updates

Do not expose password hashes in the application interface.

## Required graph behavior

Follow:
- Directed from source to target
- Reject self-follow
- Reject duplicate follows

Mutual connections:
- Users followed by both specified users

Recommendations:
- Exactly two directed hops
- Exclude current user
- Exclude already-followed users
- Remove duplicates

Popular users:
- Sort by incoming follower count, descending

Degrees of separation:
- Shortest directed path
- Maximum 4 hops
- Report no path when none exists within the limit

## Final verification

The Neo4j database must contain at least:
- 1,000 nodes
- 5,000 relationships

All 13 use cases must operate through the JavaFX application.

The report must include:
- Application screenshots
- Primary Cypher queries for each use case
- Dataset source and preparation details
- Final graph node and relationship counts
