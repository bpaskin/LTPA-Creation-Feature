package com.ibm.liberty.ltpa.sample;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.ibm.liberty.ltpa.LTPAKeyCreator;
import com.ibm.liberty.ltpa.LTPAKeyException;
import com.ibm.liberty.ltpa.LTPAKeys;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContext;

/**
 * Does the work behind the REST API with the ltpa-1.0 feature.
 *
 * <p>The keys are encrypted with a random password and written to a file in the application's
 * work directory. The encoded password goes to a second file there, readable by the owner only. It is
 * never returned to the caller.
 */
@ApplicationScoped
public class LtpaKeyService {

    private static final Logger LOG = Logger.getLogger(LtpaKeyService.class.getName());

    private static final Pattern REALM_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Set<String> ENCODINGS = Set.of("xor", "aes");
    private static final Path KEYS_FOLDER = Path.of("keys");
    private static final String PASSWORD_FILE = "keysPassword.txt";

    private static final String PASSWORD_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String PUBLIC_KEY = "com.ibm.websphere.ltpa.PublicKey";
    private static final String PRIVATE_KEY = "com.ibm.websphere.ltpa.PrivateKey";
    private static final String REALM = "com.ibm.websphere.ltpa.Realm";

    @Inject
    private ServletContext servletContext;

    public GeneratedKeys createKeys(String realm, String encoding) {
        return createKeys(realm, encoding, null, null);
    }

    public GeneratedKeys createKeys(String realm, String encoding, String password) {
        return createKeys(realm, encoding, password, null);
    }

    public GeneratedKeys createKeys(String realm, String encoding, String password, String path) {
        checkRealm(realm);
        String actualEncoding = checkEncoding(encoding);
        String actualPassword = checkPassword(password);

        try {
            LTPAKeys keys = LTPAKeyCreator.createLTPAKeys(actualPassword, realm == null ? LTPAKeyCreator.DEFAULT_REALM : realm,
                                                          actualEncoding, null);
            Path file = resolveKeysFile(path, true);
            keys.store(file);
            Path passwordFile = file.resolveSibling(PASSWORD_FILE);
            storePrivately(passwordFile, keys.getEncodedPassword());
            return new GeneratedKeys(actualEncoding, formatPath(file), formatPath(passwordFile), summarize(keys));
        } catch (LTPAKeyException | IOException e) {
            throw internalError("Creating the keys failed", e);
        }
    }

    public KeySummary generatedKeys() {
        return generatedKeys(null);
    }

    public KeySummary generatedKeys(String path) {
        return readKeys(resolveKeysFile(path, false), "No keys have been generated");
    }

    private static void storePrivately(Path file, String content) throws IOException {
        if (Files.notExists(file)) {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                Files.createFile(file);
            }
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static KeySummary readKeys(Path file, String missing) {
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, missing);
        }

        try (InputStream in = Files.newInputStream(file)) {
            return summarize(LTPAKeys.load(in));
        } catch (IOException e) {
            throw internalError("Reading a keys file failed", e);
        }
    }

    private static void checkRealm(String realm) {
        if (realm != null && !REALM_NAME.matcher(realm).matches()) {
            throw new ApiException(400, "The realm may only use letters, digits, '.', '_' and '-'");
        }
    }

    private static String checkEncoding(String encoding) {
        String actualEncoding = encoding == null ? "xor" : encoding;
        if (!ENCODINGS.contains(actualEncoding)) {
            throw new ApiException(400, "The encoding must be xor or aes");
        }
        return actualEncoding;
    }

    private static String checkPassword(String password) {
        if (password == null || password.isEmpty()) {
            return randomPassword();
        }
        if (!password.equals(password.trim())) {
            throw new ApiException(400, "The key password must not start or end with whitespace");
        }
        return password;
    }

    private Path resolveKeysFile(String customPath, boolean create) {
        Path path;
        if (customPath == null || customPath.trim().isEmpty()) {
            path = KEYS_FOLDER;
        } else {
            path = Path.of(customPath.trim());
        }

        File tempDir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        Path basePath = tempDir != null ? tempDir.toPath() : Path.of(".");
        Path fullPath = path.isAbsolute() ? path.normalize() : basePath.resolve(path).normalize();

        Path file;
        if (fullPath.toString().endsWith(".keys")) {
            file = fullPath;
        } else {
            file = fullPath.resolve("ltpa.keys");
        }

        if (create) {
            Path directory = file.getParent();
            if (directory != null && Files.notExists(directory)) {
                try {
                    Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                } catch (UnsupportedOperationException e) {
                    try {
                        Files.createDirectories(directory);
                    } catch (IOException io) {
                        throw internalError("Creating the keys directory failed", io);
                    }
                } catch (IOException e) {
                    throw internalError("Creating the keys directory failed", e);
                }
            }
        }
        return file;
    }

    private String formatPath(Path file) {
        File tempDir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (tempDir != null) {
            Path tempPath = tempDir.toPath().normalize();
            if (file.startsWith(tempPath)) {
                return tempPath.relativize(file).toString();
            }
        }
        return file.toString();
    }

    private static String randomPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }

    private static KeySummary summarize(LTPAKeys keys) {
        Properties props = keys.getProperties();
        return new KeySummary(props.getProperty(REALM),
                              fingerprint(props.getProperty(PUBLIC_KEY)), fingerprint(props.getProperty(PRIVATE_KEY)));
    }

    private static String fingerprint(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException internalError(String what, Exception cause) {
        LOG.log(Level.SEVERE, what, cause);
        return new ApiException(500, "The request could not be processed");
    }
}
