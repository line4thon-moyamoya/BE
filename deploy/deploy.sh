#!/usr/bin/env bash
set -Eeuo pipefail

deploy_sha="${1:?Usage: bash deploy/deploy.sh COMMIT_SHA}"
if [[ ! "$deploy_sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Expected a full Git commit SHA." >&2
  exit 1
fi

cd "$HOME/BE"
test -f .env
git diff --exit-code --quiet
git diff --cached --exit-code --quiet
sudo -n docker compose config --quiet
sudo -n docker compose ps --status running --services | grep -qx db

git fetch origin main
if [[ "$(git rev-parse origin/main)" != "$deploy_sha" ]]; then
  echo "Skipping superseded deployment: main has moved to another commit."
  exit 0
fi
git switch main
git merge --ff-only "$deploy_sha"
test "$(git rev-parse HEAD)" = "$deploy_sha"

# Build before replacing running containers. Keep the existing DB and .env.
sudo -n docker compose config --quiet
sudo -n docker compose build app tts-processor
sudo -n docker compose up -d --no-deps --wait --wait-timeout 180 tts-processor app

binding="$(sudo -n docker compose port app 8080 | head -n 1)"
api_port="${binding##*:}"
for attempt in {1..40}; do
  if curl --fail --silent --output /dev/null --max-time 3 \
    "http://127.0.0.1:${api_port}/api/v1/consents"; then
    echo "Deployment succeeded: $deploy_sha"
    sudo -n docker compose ps
    exit 0
  fi
  sleep 3
done

echo "API readiness check failed after deployment." >&2
sudo -n docker compose ps
exit 1
