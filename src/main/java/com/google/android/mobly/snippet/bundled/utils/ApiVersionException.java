package com.google.android.mobly.snippet.bundled.utils;

/** Raised for when an Rpc call is not supported by the Android version used. */
public class ApiVersionException extends Exception {
    private static final long serialVersionUID = 1;

    public ApiVersionException(String message) {
        super(message);
    }
}
