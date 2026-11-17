# ltpa-1.0 sample

A small REST application on Liberty that tries out the `ltpa-1.0` feature. It creates LTPA keys and stores them.

It runs on Jakarta EE 11 (`restfulWS-4.0`, `jsonb-3.0`, `mpOpenAPI-4.1`, `appSecurity-6.0`) and needs Java 17 or later.

## Run it

```sh
sample/run-sample.sh /path/to/wlp
sample/test-sample.sh /path/to/wlp
```

`run-sample.sh` builds the feature and the sample, installs the feature, creates the Liberty server
`ltpa-sample`, and starts it. `test-sample.sh` calls the API and checks the answers.

* The passwords are created on the first run and kept in `sample/.env`. Only you can read that file and git ignores it.
* The server listens on `https://localhost:19443/ltpa-sample/api/ltpa`, on the loopback address only, and speaks
  HTTPS only. The port is `sample.https.port` in [`liberty/server.xml`](liberty/server.xml).
* Liberty generates the server certificate on the first start. The test script trusts exactly that certificate, so
  the certificate check stays on.
* The API needs no user id and password, only the HTTPS connection: [`web.xml`](src/main/webapp/WEB-INF/web.xml)
  requires a confidential connection. Because there is no login, the response never contains the key password.

Stop the server with `/path/to/wlp/bin/server stop ltpa-sample`.

## Try it in the browser

The `mpOpenAPI-4.1` feature describes the API and shows it in Swagger UI, so you can try every request from the browser:

* the interactive page: `https://localhost:19443/openapi/ui`
* the OpenAPI document: `https://localhost:19443/openapi`

The browser warns about the certificate, because Liberty generated it for this server. The descriptions in the page come
from the annotations in [`LtpaResource`](src/main/java/com/ibm/liberty/ltpa/sample/LtpaResource.java).

## The API

`POST /api/ltpa/keys` creates an encrypted `ltpa.keys` file with a specified or random password. The encoded password for
`keysPassword` is written to `keys/keysPassword.txt` next to it, readable by the owner only. It is not returned.

| Request | What it does |
|---|---|
| `POST /api/ltpa/keys?realm=name&encoding=xor` or `aes` | Creates keys with a random password and writes `keys/ltpa.keys`. |
| `POST /api/ltpa/keys?password=secret` | Creates keys with the specified key password and writes `keys/ltpa.keys`. |
| `POST /api/ltpa/keys?path=custom/path` | Creates keys and writes them to the specified path or directory. |
| `GET /api/ltpa/keys` | Reads that file back and shows its fingerprints. |
| `GET /api/ltpa/keys?path=custom/path` | Reads the file from the specified path and shows its fingerprints. |

The responses never contain key material or the key password. They contain fingerprints (the first bytes of a
SHA-256) and the names of the files that were written.

```sh
curl --cacert cert.pem -X POST "https://localhost:19443/ltpa-sample/api/ltpa/keys?encoding=aes"
```

The files are written with `LTPAKeys.store(Path)` into the work directory of the application, readable by
the owner only. The code is in [`LtpaKeyService`](src/main/java/com/ibm/liberty/ltpa/sample/LtpaKeyService.java).

## Things to know

* The sample is for trying things out. It listens on the loopback address only. Anyone who can reach the port can
  replace the keys, so a real deployment would add OpenID Connect or SAML and keep the key password in a secret store.
* Realms and encodings are checked against an allowlist, and errors only show a general message. Details go to
  the server log, without secrets.
