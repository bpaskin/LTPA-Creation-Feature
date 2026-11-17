package com.ibm.liberty.ltpa.sample;

/**
 * What the API tells a caller about a key set. It never contains key material, only
 * fingerprints that let you compare key sets.
 *
 * @param realm                 the realm stored in the keys
 * @param publicKeyFingerprint  start of the SHA-256 of the public key
 * @param privateKeyFingerprint start of the SHA-256 of the stored (encrypted) private key
 */
public record KeySummary(String realm, String publicKeyFingerprint, String privateKeyFingerprint) {
}
