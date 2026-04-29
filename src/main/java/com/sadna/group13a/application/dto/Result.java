package com.sadna.group13a.application.dto;

import java.util.Optional;

/**
 * A generic wrapper for service layer results.
 * Encapsulates success state, error messages, and returned data.
 */
public class Result<T> {
    private final boolean success;
    private final String errorMessage;
    private final T data;

    private Result(boolean success, String errorMessage, T data) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, null, data);
    }

    public static <T> Result<T> success() {
        return new Result<>(true, null, null);
    }

    public static <T> Result<T> failure(String errorMessage) {
        return new Result<>(false, errorMessage, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    /**
     * Helper to get data or throw if it failed.
     */
    public T getOrThrow() {
        if (!success) {
            throw new IllegalStateException("Result failed: " + errorMessage);
        }
        return data;
    }
}
