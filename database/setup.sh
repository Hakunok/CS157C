#!/usr/bin/env bash
# One-command database setup for SocialGraph.
#
#   export NEO4J_PASSWORD='choose-a-password-8+-chars'
#   ./database/setup.sh
#
# Generates the synthetic profiles if needed, creates or starts the Neo4j
# container, and runs import.cypher. Safe to run more than once.
#
# For a container created by this script, NEO4J_PASSWORD becomes its
# password. For an existing container, it must be the password already set.
set -euo pipefail

CONTAINER="${CONTAINER:-neo4j-socialgraph}"
HTTP_PORT="${HTTP_PORT:-7474}"
BOLT_PORT="${BOLT_PORT:-7687}"
VOLUME="${VOLUME:-socialgraph_data}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMPORT_DIR="$ROOT/database/import"

if [ -z "${NEO4J_PASSWORD:-}" ]; then
  echo "Set NEO4J_PASSWORD first:  export NEO4J_PASSWORD='...'" >&2
  exit 1
fi

# 1. Input files for LOAD CSV
mkdir -p "$IMPORT_DIR"
if [ ! -f "$IMPORT_DIR/users_profiles.csv" ]; then
  python3 "$ROOT/database/generate_profiles.py"
fi
cp "$ROOT/follows.csv" "$IMPORT_DIR/follows.csv"

# 2. Container: create it on first run, start it otherwise
if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  docker start "$CONTAINER" >/dev/null
else
  docker run -d --name "$CONTAINER" \
    -p "$HTTP_PORT":7474 -p "$BOLT_PORT":7687 \
    -v "$VOLUME":/data \
    -v "$IMPORT_DIR":/import \
    -e NEO4J_AUTH="neo4j/$NEO4J_PASSWORD" \
    -e NEO4J_server_memory_heap_max__size=2G \
    -e NEO4J_server_memory_pagecache_size=1G \
    neo4j:5 >/dev/null
fi

# 3. Wait until Neo4j accepts queries (up to about 2 minutes)
echo "Waiting for Neo4j..."
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
       "RETURN 1;" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

# 4. Constraints and data
echo "Importing..."
docker exec -i "$CONTAINER" cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  < "$ROOT/database/import.cypher"

echo "Done. Browser: http://localhost:$HTTP_PORT   Bolt: bolt://localhost:$BOLT_PORT"
