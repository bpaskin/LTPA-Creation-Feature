# ltpa-1.0

A Liberty user feature with a Java API to create LTPA keys in memory.

`securityUtility createLTPAKeys` creates an `ltpa.keys` file from the command line. This feature gives you the
same from Java, for automated deployments: create LTPA keys in memory, encrypted with a key password, write them to
a stream, a string, `Properties` or a file, and get the key password in encoded form (for example `{aes}...`) for
`server.xml`.

## API

Package `com.ibm.liberty.ltpa`, enabled by the feature `usr:ltpa-1.0`.

| Method | Purpose |
|---|---|
| `LTPAKeyCreator.createLTPAKeys(keyPassword)` | Like `securityUtility createLTPAKeys`: new keys, encrypted with the password. |
| `LTPAKeyCreator.createLTPAKeys(keyPassword, realm, encoding, encodingProps)` | Same, with realm and password encoding (`xor`, `aes`, ...). |

`LTPAKeyCreator.createLTPAKeys` returns an immutable `LTPAKeys` that is available as

* `getProperties()`, a `java.util.Properties` with the content of the `ltpa.keys` file,
* `getKeyFileContent()`, the `ltpa.keys` text,
* `store(OutputStream)`, the `ltpa.keys` file written to a stream,
* `store(Path)`, the `ltpa.keys` file written to disk. A new file is readable and writable by its owner only, and an
  existing file is replaced.
* `getEncodedPassword()`, the key password in encoded form (`{aes}...`) for `<ltpa keysPassword="..."/>`.

`LTPAKeys.load(InputStream)` reads a key set back. The encoded password is not part of the file, so
`getEncodedPassword()` returns `null` for a loaded set.

### Example

```java
Map<String, String> aes = Collections.singletonMap(LTPAKeyCreator.AES_KEY, aesKeyBase64); // value of wlp.aes.encryption.key
LTPAKeys keys = LTPAKeyCreator.createLTPAKeys(keyPassword, null, "aes", aes);

keys.store(ltpaKeysPath);                          // the ltpa.keys file
String keysPassword = keys.getEncodedPassword();   // {aes}... for server.xml
```

```xml
<ltpa keysFileName="${server.config.dir}/resources/security/ltpa.keys" keysPassword="{aes}..."/>
```

`LTPAKeys.toString()` does not print key material. Keep the key password out of logs.

## Build and install

The Liberty jars are not on Maven Central. Install the ones that are needed once, then build:

```sh
mvn generate-sources -Pinstall-liberty-jars -DwlpHome=/path/to/wlp
mvn clean package
scripts/install-feature.sh /path/to/wlp
```

`scripts/install-feature.sh` copies the bundle, `ltpa-1.0.mf` and the feature translation into
`<user dir>/extension/lib`. The user dir is `WLP_USER_DIR` (environment or `etc/server.env`), else `<wlp>/usr`.

Enable the feature in `server.xml` together with a security feature, and use the API from an application:

```xml
<feature>servlet-6.1</feature>
<feature>appSecurity-6.0</feature>   <!-- the appSecurity version that fits the other features -->
<feature>usr:ltpa-1.0</feature>
```

### Java EE and Jakarta EE levels

`ltpa-1.0` is one feature for every level. Pair it with the `appSecurity` feature of the server's level:

| Platform | Servlet feature | Security feature to enable |
|---|---|---|
| Java EE 7 | `servlet-3.1` | `appSecurity-2.0` |
| Java EE 8 | `servlet-4.0` | `appSecurity-2.0` or `3.0` |
| Jakarta EE 9.1 | `servlet-5.0` | `appSecurity-4.0` |
| Jakarta EE 10 | `servlet-6.0` | `appSecurity-5.0` |
| Jakarta EE 11 | `servlet-6.1` | `appSecurity-6.0` |

Tested on WebSphere Liberty 26.0.0.6 with `servlet-3.1` + `appSecurity-2.0`, `servlet-4.0` + `appSecurity-2.0` and
`servlet-6.1` + `appSecurity-6.0`. The other rows follow the same pattern but are not tested.

## Sample application

[`sample/`](sample) holds a small REST application for Jakarta EE 11 that uses the feature, with scripts that build
it, run it on Liberty over HTTPS, and test it:

```sh
sample/run-sample.sh /path/to/wlp
sample/test-sample.sh /path/to/wlp
```

See [`sample/README.md`](sample/README.md).

## Things to know

* **Enable a security feature yourself.** `ltpa-1.0` has no feature dependency, because the `appSecurity` features
  have different names for Java EE (`appSecurity-2.0`, `3.0`) and Jakarta EE (`appSecurity-4.0` and later), and one
  feature cannot depend on both. Without a security feature the server starts, but the API throws an
  `LTPAKeyException` that asks for one.
* **Java 8 or later.** The bundle is built for Java 8. Jakarta EE 11 itself needs Java 17 or later.
* **The LTPA code is Liberty's own.** The key generation and encryption call the code behind
  `securityUtility createLTPAKeys`. Keys therefore always match what that Liberty installation expects, including
  FIPS mode. The code is internal to Liberty, so it is reached by reflection through the bundle
  `com.ibm.ws.crypto.ltpakeyutil`. A future Liberty release that renames `LTPAKeyUtil` or
  `LTPAKeyFileUtilityImpl.generateLTPAKeys` needs an update of `LibertyLTPACrypto`.
* **AES key.** `wlp.aes.encryption.key` is a Base64 encoded 256 bit key. Pass it as `LTPAKeyCreator.AES_KEY`
  (`aes.key`) in the encoding properties. The server must have the same value in its `wlp.aes.encryption.key`
  variable to decode `keysPassword`.
* **Key password.** It must not start or end with whitespace (Liberty removes it when encoding) and should be
  ASCII, because Liberty converts it to bytes with the platform default charset.
* Outside of Liberty the classes `com.ibm.ws.crypto.ltpakeyutil.*` are looked up on the class path instead of
  through OSGi. The unit tests use that, with a stand-in for `PasswordUtil`, which needs a Liberty installation.

---

This README was created by Bob.
