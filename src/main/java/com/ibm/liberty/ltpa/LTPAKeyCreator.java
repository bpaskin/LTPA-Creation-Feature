package com.ibm.liberty.ltpa;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import com.ibm.liberty.ltpa.LibertyLTPACrypto.RawKeys;
import com.ibm.websphere.crypto.PasswordUtil;

/**
 * Creates LTPA keys in memory. It does what {@code securityUtility createLTPAKeys} does.
 *
 * <pre>{@code
 * LTPAKeys keys = LTPAKeyCreator.createLTPAKeys(password, null, "aes", Collections.singletonMap(AES_KEY, aesKey));
 * keys.store(Path.of("ltpa.keys"));
 * String keysPassword = keys.getEncodedPassword();
 * }</pre>
 *
 * <p>Liberty turns the key password into bytes with the platform default charset when it opens
 * the keys file, and so does this class. If you create the keys on a different platform than
 * the one that runs Liberty, stick to an ASCII password.
 */
public final class LTPAKeyCreator {

    /** Name of the encoding property that carries the AES key. */
    public static final String AES_KEY = "aes.key";

    /** The realm that {@code securityUtility createLTPAKeys} uses. */
    public static final String DEFAULT_REALM = "defaultRealm";

    private LTPAKeyCreator() {
    }

    /**
     * Creates new LTPA keys and encrypts them with your key password. The password is encoded
     * with Liberty's default encoding.
     *
     * @param keyPassword the key password in plain text
     * @return the encrypted keys. {@link LTPAKeys#getEncodedPassword()} gives you the encoded password.
     * @throws LTPAKeyException if the keys cannot be created
     */
    public static LTPAKeys createLTPAKeys(String keyPassword) throws LTPAKeyException {
        return createLTPAKeys(keyPassword, DEFAULT_REALM, null, null);
    }

    /**
     * Creates new LTPA keys and encrypts them with your key password.
     *
     * @param keyPassword        the key password in plain text
     * @param realm              the realm to store in the keys, or {@code null} for {@link #DEFAULT_REALM}
     * @param passwordEncoding   how to encode the returned password, for example {@code xor} or
     *                           {@code aes}. Use {@code null} for Liberty's default.
     * @param encodingProperties extra settings for the encoding, or {@code null}. For {@code aes},
     *                           set {@value #AES_KEY} to the Base64 encoded 256 bit key that the
     *                           server has in {@code wlp.aes.encryption.key}.
     * @return the encrypted keys. {@link LTPAKeys#getEncodedPassword()} gives you the encoded password.
     * @throws LTPAKeyException if the keys cannot be created
     */
    public static LTPAKeys createLTPAKeys(String keyPassword, String realm, String passwordEncoding, Map<String, String> encodingProperties) throws LTPAKeyException {
        return createLTPAKeys(keyPassword, realm, passwordEncoding, encodingProperties, LIBERTY_ENCODER);
    }

    static LTPAKeys createLTPAKeys(String keyPassword, String realm, String passwordEncoding, Map<String, String> encodingProperties, PasswordEncoder encoder) throws LTPAKeyException {
        return encrypt(generateRawKeys(), realm, keyPassword, passwordEncoding, encodingProperties, encoder);
    }

    private static RawKeys generateRawKeys() throws LTPAKeyException {
        try {
            return LibertyLTPACrypto.generateRawKeys();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new LTPAKeyException("Unable to create LTPA keys, the Liberty LTPA code is not available. Enable a security feature such as appSecurity-2.0 or later in server.xml", e);
        }
    }

    private static LTPAKeys encrypt(RawKeys raw, String realm, String keyPassword, String passwordEncoding, Map<String, String> encodingProperties, PasswordEncoder encoder) throws LTPAKeyException {
        if (keyPassword == null || keyPassword.isEmpty()) {
            throw new IllegalArgumentException("The key password must not be empty");
        }

        if (!keyPassword.equals(keyPassword.trim())) {
            throw new IllegalArgumentException("The key password must not start or end with whitespace, Liberty removes it when encoding the password");
        }
        String encodedPassword;
        try {
            encodedPassword = encoder.encode(keyPassword, passwordEncoding, encodingProperties);
        } catch (Exception e) {
            throw new LTPAKeyException("Unable to encode the key password", e);
        }

        byte[] passwordBytes = keyPassword.getBytes();
        try {
            Properties props = LibertyLTPACrypto.encrypt(passwordBytes, raw, realm == null ? DEFAULT_REALM : realm);
            return new LTPAKeys(props, encodedPassword);
        } catch (Exception e) {
            throw new LTPAKeyException("Unable to encrypt LTPA keys", e);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    /** Lets the tests swap in their own encoder, so they do not need a running Liberty. */
    @FunctionalInterface
    interface PasswordEncoder {
        String encode(String password, String encoding, Map<String, String> properties) throws Exception;
    }

    private static final PasswordEncoder LIBERTY_ENCODER = (password, encoding, properties) -> {
        String actualEncoding = encoding == null ? PasswordUtil.getDefaultEncoding() : encoding;

        return properties == null ? PasswordUtil.encode(password, actualEncoding) : PasswordUtil.encode(password, actualEncoding, properties);
    };
}
