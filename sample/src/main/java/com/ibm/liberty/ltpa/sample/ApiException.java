package com.ibm.liberty.ltpa.sample;

/**
 * An error that is safe to show to the caller. The message never holds more than we want to say.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
