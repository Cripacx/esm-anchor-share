#!/usr/bin/env bash
# Uploads the Fabric and NeoForge jars of the current version to Modrinth.
# Versions that already exist there are skipped, so a failed upload is retried by the next push.
#
# Environment:
#   MODRINTH_TOKEN    personal access token with "Create versions", "Read projects" and "Read versions"
#   MODRINTH_PROJECT  project id or slug
#   VERSION, MINECRAFT, CHANGELOG_FILE
set -euo pipefail

: "${MODRINTH_TOKEN:?}" "${MODRINTH_PROJECT:?}" "${VERSION:?}" "${MINECRAFT:?}" "${CHANGELOG_FILE:?}"

API=https://api.modrinth.com/v2
USER_AGENT="Cripacx/esm-anchor-share (https://github.com/Cripacx/esm-anchor-share)"
ESM_PROJECT=5IIKsxiL
FABRIC_API_PROJECT=P7dR8mSH

api() {
  curl --fail-with-body --silent --show-error -A "$USER_AGENT" -H "Authorization: $MODRINTH_TOKEN" "$@"
}

project_id=$(api "$API/project/$MODRINTH_PROJECT" | jq -r .id)
existing=$(api "$API/project/$project_id/version" | jq -r '.[].version_number')

publish() {
  local loader=$1 jar=$2 dependencies=$3
  local number="$VERSION+$MINECRAFT-$loader"
  if grep -qxF "$number" <<< "$existing"; then
    echo "$number is already on Modrinth"
    return
  fi

  local data
  data=$(mktemp)
  jq -n \
    --arg name "ESM Anchor Share $VERSION ($loader)" \
    --arg number "$number" \
    --arg minecraft "$MINECRAFT" \
    --arg loader "$loader" \
    --arg project "$project_id" \
    --rawfile changelog "$CHANGELOG_FILE" \
    --argjson dependencies "$dependencies" \
    '{
      name: $name,
      version_number: $number,
      changelog: $changelog,
      dependencies: $dependencies,
      game_versions: [$minecraft],
      version_type: "release",
      loaders: [$loader],
      featured: true,
      status: "listed",
      environment: "client_only_server_optional",
      project_id: $project,
      file_parts: ["jar"],
      primary_file: "jar"
    }' > "$data"

  api -X POST "$API/version" \
    -F "data=<$data;type=application/json" \
    -F "jar=@$jar;type=application/java-archive" \
    | jq -r '"Published \(.version_number) to Modrinth as version \(.id)"'
}

esm="{\"project_id\": \"$ESM_PROJECT\", \"dependency_type\": \"required\"}"
fabric_api="{\"project_id\": \"$FABRIC_API_PROJECT\", \"dependency_type\": \"required\"}"

publish fabric "fabric/build/libs/ESMAnchorShare-Fabric-$MINECRAFT-$VERSION.jar" "[$esm, $fabric_api]"
publish neoforge "neoforge/build/libs/ESMAnchorShare-NeoForge-$MINECRAFT-$VERSION.jar" "[$esm]"
