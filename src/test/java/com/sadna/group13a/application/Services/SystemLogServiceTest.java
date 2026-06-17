package com.sadna.group13a.application.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SystemLogService's bounded in-memory event/error logs.
 */
class SystemLogServiceTest {

    private SystemLogService service;

    @BeforeEach
    void setUp() {
        service = new SystemLogService();
    }

    @Test
    void givenLogEventCalledOnce_whenGettingEventLog_thenReturnsThatOneEntry() {
        service.logEvent("something happened");

        List<String> eventLog = service.getEventLog();

        assertEquals(1, eventLog.size());
        assertTrue(eventLog.get(0).contains("something happened"));
    }

    @Test
    void givenLogErrorCalledOnce_whenGettingErrorLog_thenReturnsThatOneEntry() {
        service.logError("something failed");

        List<String> errorLog = service.getErrorLog();

        assertEquals(1, errorLog.size());
        assertTrue(errorLog.get(0).contains("something failed"));
    }

    @Test
    void givenEventsAndErrors_whenLogged_thenKeptInSeparateLogs() {
        service.logEvent("event message");
        service.logError("error message");

        List<String> eventLog = service.getEventLog();
        List<String> errorLog = service.getErrorLog();

        assertTrue(eventLog.stream().noneMatch(entry -> entry.contains("error message")));
        assertTrue(errorLog.stream().noneMatch(entry -> entry.contains("event message")));
    }

    @Test
    void givenLogEventCalled1001Times_whenGettingEventLog_thenOnly1000NewestEntriesKept() {
        for (int i = 0; i < 1001; i++) {
            service.logEvent("event-" + i);
        }

        List<String> eventLog = service.getEventLog();

        assertEquals(1000, eventLog.size());
        assertTrue(eventLog.stream().noneMatch(entry -> entry.endsWith("event-0")));
        assertTrue(eventLog.get(eventLog.size() - 1).contains("event-1000"));
    }

    @Test
    void givenLogErrorCalled1001Times_whenGettingErrorLog_thenOnly1000NewestEntriesKept() {
        for (int i = 0; i < 1001; i++) {
            service.logError("error-" + i);
        }

        List<String> errorLog = service.getErrorLog();

        assertEquals(1000, errorLog.size());
        assertTrue(errorLog.stream().noneMatch(entry -> entry.endsWith("error-0")));
        assertTrue(errorLog.get(errorLog.size() - 1).contains("error-1000"));
    }

    @Test
    void givenReturnedLogs_whenModified_thenInternalLogsUnaffected() {
        service.logEvent("event message");
        service.logError("error message");

        List<String> eventLog = service.getEventLog();
        List<String> errorLog = service.getErrorLog();
        eventLog.clear();
        errorLog.clear();

        assertEquals(1, service.getEventLog().size());
        assertEquals(1, service.getErrorLog().size());
    }
}
