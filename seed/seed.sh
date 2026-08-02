#!/bin/bash
# ============================================================
# Nomos - Seed Data Loader
# Reads seed-data.json and populates the graph via Admin API
# Usage: ./seed.sh [BASE_URL]
# Default: http://localhost:8080/nomos/v1/api/admin
# ============================================================

BASE_URL="${1:-http://localhost:8080/nomos/v1/api/admin}"
SEED_FILE="$(dirname "$0")/seed-data.json"

echo "=== Nomos Seed Loader ==="
echo "Base URL: $BASE_URL"
echo "Seed file: $SEED_FILE"
echo ""

# Step 1: Create IDPs
echo "--- Creating IDPs ---"
jq -c '.idps[]' "$SEED_FILE" | while read -r item; do
  echo "  IDP: $(echo "$item" | jq -r '.name')"
  curl -s -X POST "$BASE_URL/idp" -H "Content-Type: application/json" -d "$item" | jq -r '.message // .error'
done
echo ""

# Step 2: Create Apps
echo "--- Creating Apps ---"
jq -c '.apps[]' "$SEED_FILE" | while read -r item; do
  echo "  App: $(echo "$item" | jq -r '.appId')"
  curl -s -X POST "$BASE_URL/app" -H "Content-Type: application/json" -d "$item" | jq -r '.message // .error'
done
echo ""

# Step 3: Link Apps to IDPs
echo "--- Linking Apps to IDPs ---"
jq -c '.links[]' "$SEED_FILE" | while read -r item; do
  APP_ID=$(echo "$item" | jq -r '.appId')
  IDP_NAME=$(echo "$item" | jq -r '.idpName')
  BODY=$(echo "$item" | jq '{audience, label}')
  echo "  $APP_ID → $IDP_NAME ($(echo "$item" | jq -r '.audience'))"
  curl -s -X POST "$BASE_URL/app/$APP_ID/idp/$IDP_NAME" -H "Content-Type: application/json" -d "$BODY" | jq -r '.message // .error'
done
echo ""

# Step 4: Create Proxies
echo "--- Creating Proxies ---"
jq -c '.proxies[]' "$SEED_FILE" | while read -r item; do
  echo "  Proxy: $(echo "$item" | jq -r '.name') ($(echo "$item" | jq -r '.defaultPolicy'))"
  curl -s -X POST "$BASE_URL/proxy" -H "Content-Type: application/json" -d "$item" | jq -r '.message // .error'
done
echo ""

# Step 5: Grant Access
echo "--- Granting Access ---"
jq -c '.access[]' "$SEED_FILE" | while read -r item; do
  echo "  $(echo "$item" | jq -r '.appId') [$(echo "$item" | jq -r '.audience')] → $(echo "$item" | jq -r '.proxyName')"
  curl -s -X POST "$BASE_URL/access" -H "Content-Type: application/json" -d "$item" | jq -r '.message // .error'
done
echo ""

# Step 6: Create Rules
echo "--- Creating Rules ---"
jq -c '.rules[]' "$SEED_FILE" | while read -r item; do
  PROXY=$(echo "$item" | jq -r '.proxyName')
  IDP=$(echo "$item" | jq -r '.idpName')
  echo "  Rules for $PROXY + $IDP"
  curl -s -X POST "$BASE_URL/rules" -H "Content-Type: application/json" -d "$item" | jq -r '.message // .error'
done
echo ""

echo "=== Done ==="
