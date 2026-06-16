package com.sadna.group13a.infrastructure.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflective guard placed in front of a repository: before delegating any business
 * method to the real repository it verifies the data store is connected, so callers
 * get a meaningful {@link com.sadna.group13a.domain.shared.PersistenceUnavailableException}
 * instead of operating against an unavailable store (issue #228).
 *
 * <p>{@link Object} methods ({@code equals}/{@code hashCode}/{@code toString}) are
 * delegated without a connectivity check.
 */
public class PersistenceAvailabilityInvocationHandler implements InvocationHandler {

    private final Object target;
    private final DatabaseConnectionManager connectionManager;

    public PersistenceAvailabilityInvocationHandler(Object target, DatabaseConnectionManager connectionManager) {
        this.target = target;
        this.connectionManager = connectionManager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() != Object.class) {
            connectionManager.verifyConnected();
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
