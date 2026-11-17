package com.ibm.liberty.ltpa;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Talks to Liberty's own LTPA key code, the same code that {@code securityUtility createLTPAKeys}
 * and the LTPA runtime use. That way the keys always have the format and encryption this Liberty
 * expects, FIPS mode included.
 *
 * <p>The code sits in the internal package {@code com.ibm.ws.crypto.ltpakeyutil}, which a user
 * feature is not allowed to import. So we load it from the Liberty bundle that holds it. Outside
 * of OSGi, in a unit test for example, the class path is used instead.
 */
final class LibertyLTPACrypto {

    private static final String PACKAGE = "com.ibm.ws.crypto.ltpakeyutil.";
    private static final String BUNDLE = "com.ibm.ws.crypto.ltpakeyutil";

    private LibertyLTPACrypto() {}

    static final class RawKeys {
        final byte[] secretKey;
        final byte[] privateKey;
        final byte[] publicKey;

        RawKeys(byte[] secretKey, byte[] privateKey, byte[] publicKey) {
            this.secretKey = secretKey;
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    static RawKeys generateRawKeys() throws ReflectiveOperationException {
        Class<?> util = load(PACKAGE + "LTPAKeyUtil");
        byte[] secret = (byte[]) util.getMethod("generateSharedKey").invoke(null);
        Object pair = util.getMethod("generateLTPAKeyPair").invoke(null);
        Object privateKey = pair.getClass().getMethod("getPrivate").invoke(pair);
        Object publicKey = pair.getClass().getMethod("getPublic").invoke(pair);

        return new RawKeys(secret,
                           (byte[]) privateKey.getClass().getMethod("getEncoded").invoke(privateKey),
                           (byte[]) publicKey.getClass().getMethod("getEncoded").invoke(publicKey));
    }

    /**
     * Encrypts the secret key and the private key with the password and gives back what would
     * go into an {@code ltpa.keys} file.
     */
    static Properties encrypt(byte[] password, RawKeys keys, String realm) throws Exception {
        Class<?> impl = load(PACKAGE + "LTPAKeyFileUtilityImpl");
        Method generate = impl.getDeclaredMethod("generateLTPAKeys", byte[].class, byte[].class, byte[].class,
                                                 byte[].class, String.class);
        generate.setAccessible(true);

        try {
            return (Properties) generate.invoke(impl.getDeclaredConstructor().newInstance(), password, keys.secretKey, keys.privateKey, keys.publicKey, realm);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception ? (Exception) cause : e;
        }
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, true, LibertyLTPACrypto.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return Osgi.load(name, e);
        }
    }

    /** Lives in its own class so the OSGi classes are only needed when we run inside Liberty. */
    private static final class Osgi {
        static Class<?> load(String name, ClassNotFoundException notFound) throws ClassNotFoundException {

            Bundle self = FrameworkUtil.getBundle(LibertyLTPACrypto.class);

            if (self != null && self.getBundleContext() != null) {
                for (Bundle bundle : self.getBundleContext().getBundles()) {
                    if (BUNDLE.equals(bundle.getSymbolicName())) {
                        return bundle.loadClass(name);
                    }
                }
            }
            throw notFound;
        }
    }
}
