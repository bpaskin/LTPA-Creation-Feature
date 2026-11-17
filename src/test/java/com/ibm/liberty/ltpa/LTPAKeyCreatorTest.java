package com.ibm.liberty.ltpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ibm.websphere.crypto.PasswordUtil;
import com.ibm.ws.crypto.ltpakeyutil.KeyEncryptor;
import com.ibm.ws.crypto.ltpakeyutil.LTPAKeyFileUtility;
import com.ibm.ws.crypto.ltpakeyutil.LTPAKeyUtil;
import com.ibm.ws.crypto.ltpakeyutil.LTPAPrivateKey;
import com.ibm.ws.crypto.ltpakeyutil.LTPAPublicKey;

class LTPAKeyCreatorTest {

    private static final String SECRET_KEY = LTPAKeyFileUtility.KEYIMPORT_SECRETKEY;
    private static final String PRIVATE_KEY = LTPAKeyFileUtility.KEYIMPORT_PRIVATEKEY;
    private static final String PUBLIC_KEY = LTPAKeyFileUtility.KEYIMPORT_PUBLICKEY;
    private static final String REALM = LTPAKeyFileUtility.KEYIMPORT_REALM;

    /** Stands in for PasswordUtil, which needs a Liberty installation. */
    private static final LTPAKeyCreator.PasswordEncoder FAKE_ENCODER =
            (password, encoding, props) -> "{" + encoding + "}" + new StringBuilder(password).reverse() + (props == null ? "" : props);

    @Test
    void keysCanBeDecryptedWithThePassword() throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("keyPw", "myRealm", "aes", null, FAKE_ENCODER);

        assertEquals("{aes}wPyek", keys.getEncodedPassword());
        Properties props = keys.getProperties();
        assertEquals("myRealm", props.getProperty(REALM));

        KeyEncryptor encryptor = new KeyEncryptor("keyPw".getBytes());
        byte[] secret = encryptor.decrypt(decode(props, SECRET_KEY));
        byte[] privateKey = encryptor.decrypt(decode(props, PRIVATE_KEY));
        assertEquals(24, secret.length);
        // the decrypted keys are a working key pair
        byte[] publicKey = decode(props, PUBLIC_KEY);
        byte[] message = "token".getBytes();
        byte[][] rawPrivateKey = LTPAKeyUtil.getRawKey(new LTPAPrivateKey(privateKey));
        LTPAKeyUtil.setRSAKey(rawPrivateKey);
        byte[] signature = LTPAKeyUtil.signISO9796(rawPrivateKey, message, 0, message.length);
        assertTrue(LTPAKeyUtil.verifyISO9796(LTPAKeyUtil.getRawKey(new LTPAPublicKey(publicKey)), message, 0, message.length, signature, 0, signature.length));
    }

    @Test
    void defaultRealmIsUsedWhenNoneIsGiven() throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);

        assertEquals(LTPAKeyCreator.DEFAULT_REALM, keys.getProperties().getProperty(REALM));
    }

    @Test
    void aesKeyPropertyNameMatchesLiberty() {
        assertEquals(PasswordUtil.PROPERTY_AES_KEY, LTPAKeyCreator.AES_KEY);
    }

    @Test
    void everyCallCreatesNewKeys() throws Exception {
        LTPAKeys first = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);
        LTPAKeys second = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);

        assertNotEquals(first.getProperties().getProperty(SECRET_KEY), second.getProperties().getProperty(SECRET_KEY));
        assertNotEquals(first.getProperties().getProperty(PUBLIC_KEY), second.getProperties().getProperty(PUBLIC_KEY));
    }

    @Test
    void keysSurviveStoreAndLoad() throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keys.store(out);
        LTPAKeys loaded = LTPAKeys.load(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(keys.getProperties(), loaded.getProperties());
        assertEquals(null, loaded.getEncodedPassword());
    }

    @Test
    void keysCanBeWrittenToAFileAndLoadedAgain(@TempDir Path dir) throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("pw", "defaultRealm", "xor", null, FAKE_ENCODER);
        Path file = dir.resolve("ltpa.keys");

        keys.store(file);
        keys.store(file);

        try (InputStream in = Files.newInputStream(file)) {
            assertEquals(keys.getProperties(), LTPAKeys.load(in).getProperties());
        }
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(file));
        }
    }

    @Test
    void textFormatMatchesTheLibertyKeyFileFormat() throws Exception {
        String content = LTPAKeyCreator.createLTPAKeys("pw", "defaultRealm", "xor", null, FAKE_ENCODER).getKeyFileContent();

        assertTrue(content.contains("com.ibm.websphere.ltpa.3DESKey="));
        assertTrue(content.contains("com.ibm.websphere.ltpa.PrivateKey="));
        assertTrue(content.contains("com.ibm.websphere.ltpa.PublicKey="));
        assertTrue(content.contains("com.ibm.websphere.ltpa.Realm=defaultRealm"));
        assertTrue(content.contains("com.ibm.websphere.ltpa.version="));
    }

    @Test
    void toStringDoesNotLeakKeyMaterial() throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);

        assertEquals("LTPAKeys[]", keys.toString());
    }

    @Test
    void returnedPropertiesAreCopies() throws Exception {
        LTPAKeys keys = LTPAKeyCreator.createLTPAKeys("pw", null, "xor", null, FAKE_ENCODER);

        keys.getProperties().clear();

        assertFalse(keys.getProperties().isEmpty());
    }

    @Test
    void invalidPasswordsAreRejected() {
        for (String password : new String[] { null, "", " leading", "trailing ", "\ttab" }) {
            assertThrows(IllegalArgumentException.class, () -> LTPAKeyCreator.createLTPAKeys(password, null, "xor", null, FAKE_ENCODER));
        }
    }

    @Test
    void encoderFailureIsReported() {
        LTPAKeyCreator.PasswordEncoder failing = (password, encoding, props) -> {
            throw new IllegalStateException("no such encoding");
        };

        LTPAKeyException e = assertThrows(LTPAKeyException.class,
                                          () -> LTPAKeyCreator.createLTPAKeys("pw", null, "nope", null, failing));
        assertTrue(e.getCause() instanceof IllegalStateException);
    }

    private static byte[] decode(Properties props, String name) {
        return Base64.getDecoder().decode(props.getProperty(name));
    }
}
