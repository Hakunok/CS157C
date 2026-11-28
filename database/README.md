# Neo4j Database Setup

How to run the SocialGraph database on your own machine. Each teammate runs a local copy, because the full dataset (1.77M follows) is larger than the AuraDB Free limit of 400,000 relationships.

## 1. Requirements

- Docker Desktop, running
- Python 3
- About 4 GB of free memory for the container

## Quick start (steps 2–4 in one command)

```bash
export NEO4J_PASSWORD='choose-a-password'   # 8+ characters
./database/setup.sh
```

On a new machine, this sets that password on the container. If the container already exists, use the password it already has. To do it step by step instead, follow the sections below.

## 2. Generate the synthetic profiles

From the repo root:

```bash
python3 database/generate_profiles.py
cp follows.csv database/import/
```

This creates `database/import/users_profiles.csv` and `database/demo_accounts.csv`. Never commit the demo accounts file.

## 3. Start Neo4j

From the repo root:

```bash
docker run -d --name neo4j-socialgraph \
  -p 7474:7474 -p 7687:7687 \
  -v socialgraph_data:/data \
  -v "$PWD/database/import":/import \
  -e NEO4J_server_memory_heap_max__size=2G \
  -e NEO4J_server_memory_pagecache_size=1G \
  neo4j:5
```

Open http://localhost:7474, log in with `neo4j` / `neo4j`, and choose a new password when asked.

After the first run, use `docker start neo4j-socialgraph` and `docker stop neo4j-socialgraph`.

## 4. Load the data

From the repo root, with your password from step 3:

```bash
docker exec -i neo4j-socialgraph cypher-shell -u neo4j -p 'your-password' < database/import.cypher
```

This creates the constraints, then loads the users and follows in batches. It takes about 20 seconds and is safe to run again.

## 5. Run the app

```bash
export NEO4J_PASSWORD='your-password'
cd SocialGraph
mvn javafx:run
```

`NEO4J_URI` defaults to `bolt://localhost:7687` and `NEO4J_USERNAME` defaults to `neo4j`.

## 6. Check it worked

The last two lines the import prints should be `81306` users and `1768135` follows. To check again later, run this in Neo4j Browser:

```cypher
MATCH (u:User) RETURN count(u);
MATCH ()-[r:FOLLOWS]->() RETURN count(r);
```
