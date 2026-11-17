package com.ibm.liberty.ltpa;

/**
 * Thrown when LTPA keys could not be created or encrypted.
 */
public class LTPAKeyException extends Exception {

    private static final long serialVersionUID = 1L;

    public LTPAKeyException(String message) {
        super(message);
    }

    public LTPAKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
