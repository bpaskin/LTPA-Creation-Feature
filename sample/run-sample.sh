#!/bin/sh
# Builds the ltpa-1.0 feature and the sample, installs both into a Liberty installation, and starts the
# server "ltpa-sample".
# Usage: sample/run-sample.sh /path/to/wlp
# The passwords of the sample are created once, in sample/.env (only you can read it, git ignores it).
set -eu

WLP_HOME="${1:-${WLP_HOME:-}}"
[ -d "$WLP_HOME/lib" ] || { echo "Usage: $0 /path/to/wlp (or set WLP_HOME)" >&2; exit 1; }

SAMPLE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SAMPLE/.." && pwd)"

if [ -z "${WLP_USER_DIR:-}" ] && [ -f "$WLP_HOME/etc/server.env" ]; then
  WLP_USER_DIR="$(sed -n 's/^WLP_USER_DIR=//p' "$WLP_HOME/etc/server.env" | sed -n 1p)"
fi
SERVER_DIR="${WLP_USER_DIR:-$WLP_HOME/usr}/servers/ltpa-sample"

if [ ! -f "$SAMPLE/.env" ]; then
  umask 077
  secret() { openssl rand -base64 24 | tr -d '/+=\n'; }
  {
    echo "LTPA_SAMPLE_KEYSTORE_PASSWORD=$(secret)"
    echo "LTPA_SAMPLE_LTPA_PASSWORD=$(secret)"
  } > "$SAMPLE/.env"
  echo "Created the sample passwords in $SAMPLE/.env"
fi
set -a
. "$SAMPLE/.env"
set +a

mvn -q -f "$ROOT/pom.xml" clean install -DskipTests
"$ROOT/scripts/install-feature.sh" "$WLP_HOME"
mvn -q -f "$SAMPLE/pom.xml" clean package

[ -d "$SERVER_DIR" ] || "$WLP_HOME/bin/server" create ltpa-sample
cp "$SAMPLE/liberty/server.xml" "$SERVER_DIR/server.xml"
mkdir -p "$SERVER_DIR/apps"
cp "$SAMPLE/target/ltpa-sample.war" "$SERVER_DIR/apps/ltpa-sample.war"

"$WLP_HOME/bin/server" start ltpa-sample
echo "The sample listens on https://localhost:19443/ltpa-sample/api/ltpa"
echo "Test it with: sample/test-sample.sh /path/to/wlp"
