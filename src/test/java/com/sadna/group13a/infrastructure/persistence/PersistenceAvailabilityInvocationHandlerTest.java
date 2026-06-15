package com.sadna.group13a.infrastructure.persistence;

import com.sadna.group13a.domain.shared.PersistenceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PersistenceAvailabilityInvocationHandler — connectivity guard")
class PersistenceAvailabilityInvocationHandlerTest {

    interface Sample {
        String ok();
        void boom();
    }

    static class SampleImpl implements Sample {
        public String ok() { return "ok"; }
        public void boom() { throw new IllegalStateException("kaboom"); }
    }

    private Sample proxy(DatabaseConnectionManager mgr) {
        return (Sample) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Sample.class},
                new PersistenceAvailabilityInvocationHandler(new SampleImpl(), mgr));
    }

    @Test
    @DisplayName("When connected, the call is delegated to the target")
    void connected_delegates() {
        DatabaseConnectionManager mgr = mock(DatabaseConnectionManager.class);
        doNothing().when(mgr).verifyConnected();

        assertEquals("ok", proxy(mgr).ok());
        verify(mgr).verifyConnected();
    }

    @Test
    @DisplayName("When disconnected, the call is rejected with PersistenceUnavailableException")
    void disconnected_rejects() {
        DatabaseConnectionManager mgr = mock(DatabaseConnectionManager.class);
        doThrow(new PersistenceUnavailableException("down")).when(mgr).verifyConnected();

        assertThrows(PersistenceUnavailableException.class, () -> proxy(mgr).ok());
    }

    @Test
    @DisplayName("Target exceptions are unwrapped (not wrapped in InvocationTargetException)")
    void targetException_unwrapped() {
        DatabaseConnectionManager mgr = mock(DatabaseConnectionManager.class);
        doNothing().when(mgr).verifyConnected();

        assertThrows(IllegalStateException.class, () -> proxy(mgr).boom());
    }

    @Test
    @DisplayName("Object methods (toString) are delegated without a connectivity check")
    void objectMethods_skipCheck() {
        DatabaseConnectionManager mgr = mock(DatabaseConnectionManager.class);
        doThrow(new PersistenceUnavailableException("down")).when(mgr).verifyConnected();

        assertDoesNotThrow(() -> proxy(mgr).toString());
        verify(mgr, never()).verifyConnected();
    }
}
