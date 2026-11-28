# Dataset and Data Preparation

> DRAFT: check the items marked **TODO** before this goes into the report.

## Source

- **Dataset:** Twitter social circles (`ego-Twitter`), Stanford SNAP collection.
  **TODO:** confirm with Hakunok, who added the cleaned files (commit `42a78f6`). SNAP lists 81,306 nodes and 1,768,149 edges for this dataset, and our files have 81,306 users and 1,768,135 follows.
- **Original contents:** anonymized numeric Twitter user IDs and directed "follows" edges. It has no names, emails, bios or passwords.
- **Citation:** J. McAuley and J. Leskovec. *Learning to Discover Social Circles in Ego Networks.* NIPS, 2012. **TODO:** confirm.

## Cleaned files (repo root)

| File | Columns | Rows | Checks (run 2026-11-27) |
|---|---|---|---|
| `users.csv` | `userId` | 81,306 | no duplicate IDs |
| `follows.csv` | `source,target` (source follows target) | 1,768,135 | no self-follows, no duplicate edges, every ID exists in `users.csv` |

**TODO:** Hakunok to describe the cleaning step, for example what removed the 14 edges.

## What is real and what is synthetic

| Property | Origin |
|---|---|
| `userId` | **Real**, from the dataset |
| `FOLLOWS` relationships | **Real**, from the dataset |
| `username` | Derived: `user_<userId>` |
| `name` | **Synthetic**: random first and last name from fixed lists |
| `email` | **Synthetic**: `user_<userId>@example.com` (`example.com` is reserved for documentation, so these never reach a real person) |
| `bio` | **Synthetic**: random template, empty for about 25% of users |
| `passwordHash` | **Synthetic**: see below |

Profiles come from `database/generate_profiles.py` with a fixed random seed (157), so everyone who runs it gets the same names and bios.

## Passwords for imported accounts

The specification says imported accounts must not share a known default password.

- **20 demo accounts** get a unique random password, hashed with PBKDF2-HMAC-SHA256 (210,000 iterations, 16-byte salt), the same format `Neo4jAuthService` uses. The plain passwords are written to `database/demo_accounts.csv`, which is gitignored and shared privately within the team.
- **All other imported accounts** have an empty `passwordHash`. `Neo4jAuthService.login()` rejects an empty hash, so nobody can log in as them.
- Users who register through the app get their own hashed password as normal.

## Final graph counts

Verified on a test import, 2026-11-27. Users who register through the app add to the node count.

| Item | Count |
|---|---|
| `:User` nodes | 81,306 |
| `:FOLLOWS` relationships | 1,768,135 |

The spec's minimum is 1,000 nodes and 5,000 relationships.
