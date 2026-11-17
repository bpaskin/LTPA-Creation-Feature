#!/bin/sh
# Installs the ltpa-1.0 user feature into a Liberty installation.
# Usage: scripts/install-feature.sh /path/to/wlp
# Afterwards enable it in server.xml with <feature>usr:ltpa-1.0</feature>, together with a
# security feature such as appSecurity-2.0 or later.
set -eu

WLP_HOME="${1:-${WLP_HOME:-}}"
[ -d "$WLP_HOME/lib" ] || { echo "Usage: $0 /path/to/wlp (or set WLP_HOME)" >&2; exit 1; }

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(sed -n 's|.*<version>\(.*\)</version>.*|\1|p' "$ROOT/pom.xml" | sed -n 1p)"
JAR="$ROOT/target/com.ibm.liberty.ltpa_${VERSION}.jar"
[ -f "$JAR" ] || { echo "Build first: mvn clean package" >&2; exit 1; }

# the user directory may be moved with WLP_USER_DIR, in the environment or in etc/server.env
if [ -z "${WLP_USER_DIR:-}" ] && [ -f "$WLP_HOME/etc/server.env" ]; then
  WLP_USER_DIR="$(sed -n 's/^WLP_USER_DIR=//p' "$WLP_HOME/etc/server.env" | sed -n 1p)"
fi
EXT="${WLP_USER_DIR:-$WLP_HOME/usr}/extension/lib"
mkdir -p "$EXT/features" "$EXT/OSGI-INF/l10n"
cp "$JAR" "$EXT/"
cp "$ROOT/src/main/feature/features/ltpa-1.0.mf" "$EXT/features/"
cp "$ROOT/src/main/feature/OSGI-INF/l10n/"*.properties "$EXT/OSGI-INF/l10n/"
echo "Installed ltpa-1.0 into $EXT"
