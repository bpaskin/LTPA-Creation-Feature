#!/bin/sh
# Calls the REST API of the running sample and checks the answers.
# Usage: sample/test-sample.sh /path/to/wlp
# Start the sample first with sample/run-sample.sh.
set -eu

WLP_HOME="${1:-${WLP_HOME:-}}"
[ -d "$WLP_HOME/lib" ] || { echo "Usage: $0 /path/to/wlp (or set WLP_HOME)" >&2; exit 1; }

SAMPLE="$(cd "$(dirname "$0")" && pwd)"
[ -f "$SAMPLE/.env" ] || { echo "Run sample/run-sample.sh first" >&2; exit 1; }
set -a
. "$SAMPLE/.env"
set +a

if [ -z "${WLP_USER_DIR:-}" ] && [ -f "$WLP_HOME/etc/server.env" ]; then
  WLP_USER_DIR="$(sed -n 's/^WLP_USER_DIR=//p' "$WLP_HOME/etc/server.env" | sed -n 1p)"
fi
SERVER_DIR="${WLP_USER_DIR:-$WLP_HOME/usr}/servers/ltpa-sample"

PORT=19443
BASE="https://localhost:$PORT/ltpa-sample/api/ltpa"
CERT="$SAMPLE/.server-cert.pem"

# Trust the certificate of the sample server, so that the certificate check stays switched on.
keytool -exportcert -rfc -alias default -keystore "$SERVER_DIR/resources/security/key.p12" \
        -storetype PKCS12 -storepass:env LTPA_SAMPLE_KEYSTORE_PASSWORD -file "$CERT" >/dev/null 2>&1 \
  || { echo "Cannot read the certificate of the sample server, is it running?" >&2; exit 1; }

# Prints the HTTP status on the last line.
call() {
  curl -sS --cacert "$CERT" --resolve "localhost:$PORT:127.0.0.1" -w '\n%{http_code}' "$@"
}

FAILED=0
check() { # description, expected status, curl arguments...
  desc="$1"; want="$2"; shift 2
  out="$(call "$@")"
  status="$(printf '%s\n' "$out" | tail -n 1)"
  BODY="$(printf '%s\n' "$out" | sed '$d')"
  if [ "$status" = "$want" ]; then
    echo "ok    $desc ($status)"
  else
    echo "FAIL  $desc: expected $want, got $status"
    FAILED=1
  fi
}

field() { printf '%s' "$BODY" | sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }
expect_equal() { [ "$2" = "$3" ] && echo "ok    $1" || { echo "FAIL  $1 ($2 / $3)"; FAILED=1; }; }

check "create keys"                            201 -X POST "$BASE/keys?realm=simpleRealm&encoding=aes"
GEN_PRIVATE="$(field privateKeyFingerprint)"
case "$BODY" in *keysPassword\"*) echo "FAIL  the response contains the key password"; FAILED=1;; *) echo "ok    the response has no key password";; esac
check "create keys with password"              201 -X POST "$BASE/keys?realm=customRealm&encoding=xor&password=customPassword123"
check "read the keys back"                    200 "$BASE/keys"
expect_equal "the file holds what was returned" "$(field privateKeyFingerprint)" "$GEN_PRIVATE"
check "create keys with custom path"          201 -X POST "$BASE/keys?path=custom/location"
expect_equal "the path is custom"             "$(field keysFile)" "custom/location/ltpa.keys"
CUSTOM_PRIVATE="$(field privateKeyFingerprint)"
check "read the custom path keys back"        200 "$BASE/keys?path=custom/location"
expect_equal "the custom file holds what was returned" "$(field privateKeyFingerprint)" "$CUSTOM_PRIVATE"
check "bad encoding"                            400 -X POST "$BASE/keys?encoding=rot13"
check "bad realm"                               400 -X POST "$BASE/keys?realm=a%20b"
check "bad password with whitespace"           400 -X POST "$BASE/keys?password=%20leading"

if [ "$FAILED" -eq 0 ]; then echo "All checks passed"; else echo "Some checks failed"; exit 1; fi
