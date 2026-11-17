package com.ibm.liberty.ltpa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/**
 * A set of encrypted LTPA keys that never changes once created. It looks just like a Liberty
 * {@code ltpa.keys} file: the 3DES secret key and the RSA private key are encrypted with a key
 * password. You can save it to disk and let Liberty load it. If this API created it, it also knows
 * the encoded key password (for example {@code {aes}...}) that goes into the {@code keysPassword}
 * attribute of the {@code <ltpa>} element.
 *
 * <p>You can read the content as {@link Properties}, as a {@link String}, or write it to a stream
 * or a file.
 */
public final class LTPAKeys {

    private final Properties properties;
    private final String encodedPassword;

    LTPAKeys(Properties properties, String encodedPassword) {
        this.properties = copy(properties);
        this.encodedPassword = encodedPassword;
    }

    /**
     * Reads a key set in the {@code ltpa.keys} text format. The encoded password is not stored in
     * the file, so {@link #getEncodedPassword()} returns {@code null} for a set that was loaded.
     *
     * @param in the stream to read. It is not closed.
     * @return the key set
     * @throws IOException if the stream cannot be read or the content is malformed
     */
    public static LTPAKeys load(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);
        return new LTPAKeys(props, null);
    }

    /**
     * @return the key password in encoded form, such as {@code {aes}...}, ready to use as
     *         {@code keysPassword} in server.xml. It is {@code null} for a set that was loaded.
     */
    public String getEncodedPassword() {
        return encodedPassword;
    }

    /**
     * @return a copy of the key set as properties, named like the entries of an {@code ltpa.keys} file
     */
    public Properties getProperties() {
        return copy(properties);
    }

    /**
     * @return the key set as {@code ltpa.keys} text
     */
    public String getKeyFileContent() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            store(out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    /**
     * Writes the key set as {@code ltpa.keys} text, the way Liberty does.
     *
     * @param out the stream to write to. It is not closed.
     * @throws IOException if writing fails
     */
    public void store(OutputStream out) throws IOException {
        properties.store(out, null);
    }

    /**
     * Writes the key set to a file, for example the {@code ltpa.keys} that Liberty loads. A file
     * that exists is replaced. A new file is readable and writable by its owner only, where the
     * file system supports that.
     *
     * @param file the file to write
     * @throws IOException           if writing fails
     */
    public void store(Path file) throws IOException {
        if (Files.notExists(file)) {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                Files.createFile(file);
            }
        }

        try (OutputStream out = Files.newOutputStream(file)) {
            store(out);
        }
    }

    @Override
    public String toString() {
        return "LTPAKeys[]";
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        for (String name : source.stringPropertyNames()) {
            copy.setProperty(name, source.getProperty(name));
        }
        return copy;
    }
}
