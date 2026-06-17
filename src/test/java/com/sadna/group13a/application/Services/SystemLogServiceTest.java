package com.sadna.group13a.application.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SystemLogService.
 *
 * Pure unit tests — no mocks needed.
 */
class SystemLogServiceTest {

    private SystemLogService service;

    @BeforeEach
    void setUp() {
        service = new SystemLogService();
    }

    @Test
    void givenLogEventCalledOnce_whenGetEventLog_thenReturnsOneEntry() {
        service.logEvent("something happened");

        List<String> eventLog = service.getEventLog();

        assertEquals(1, eventLog.size());
        assertTrue(eventLog.get(0).contains("something happened"));
    }

    @Test
    void givenLogErrorCalledOnce_whenGetErrorLog_thenReturnsOneEntry() {
        service.logError("something failed");

        List<String> errorLog = service.getErrorLog();

        assertEquals(1, errorLog.size());
        assertTrue(errorLog.get(0).contains("something failed"));
    }

    @Test
    void givenEventsAndErrorsLogged_thenLogsAreSeparate() {
        service.logEvent("event message");
        service.logError("error message");

        List<String> eventLog = service.getEventLog();
        List<String> errorLog = service.getErrorLog();

        assertTrue(eventLog.stream().noneMatch(e -> e.contains("error message")));
        assertTrue(errorLog.stream().noneMatch(e -> e.contains("event message")));
    }

    @Test
    void givenLogEventCalled1001Times_thenEventLogIsCappedAt1000() {
        for (int i = 0; i < 1001; i++) {
            service.logEvent("event-" + i);
        }

        List<String> eventLog = service.getEventLog();

        assertEquals(1000, eventLog.size());
        assertTrue(eventLog.get(0).contains("event-1"));
        assertTrue(eventLog.get(eventLog.size() - 1).contains("event-1000"));
    }

    @Test
    void givenLogErrorCalled1001Times_thenErrorLogIsCappedAt1000() {
        for (int i = 0; i < 1001; i++) {
            service.logError("error-" + i);
        }

        List<String> errorLog = service.getErrorLog();

        assertEquals(1000, errorLog.size());
        assertTrue(errorLog.get(0).contains("error-1"));
        assertTrue(errorLog.get(errorLog.size() - 1).contains("error-1000"));
    }

    @Test
    void givenReturnedLogModified_thenInternalLogUnaffected() {
        service.logEvent("event message");
        service.logError("error message");

        List<String> eventLog = service.getEventLog();
        eventLog.clear();
        List<String> errorLog = service.getErrorLog();
        errorLog.clear();

        assertEquals(1, service.getEventLog().size());
        assertEquals(1, service.getErrorLog().size());
    }
}
