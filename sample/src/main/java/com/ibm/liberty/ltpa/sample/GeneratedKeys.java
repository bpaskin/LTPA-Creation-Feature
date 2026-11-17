package com.ibm.liberty.ltpa.sample;

/**
 * The result of generating a set of LTPA keys. It does not contain the key password.
 *
 * @param encoding             how the key password was encoded
 * @param keysFile             where the {@code ltpa.keys} was written, relative to the application's work directory
 * @param keysPasswordFile     where the encoded key password was written, relative to the application's work
 *                             directory. It is the value for {@code keysPassword} in server.xml.
 * @param keys                 the fingerprints of the written keys
 */
public record GeneratedKeys(String encoding, String keysFile, String keysPasswordFile, KeySummary keys) {
}
