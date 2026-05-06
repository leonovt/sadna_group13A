package com.sadna.group13a.application.Services;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

// Maintains the in-memory event log and error log required by SLR-8.
// Capped at MAX_ENTRIES so memory stays bounded even under sustained load.
@Service
public class SystemLogService {

    private static final int MAX_ENTRIES = 1000;

    private final Deque<String> eventLog = new ConcurrentLinkedDeque<>();
    private final Deque<String> errorLog = new ConcurrentLinkedDeque<>();

    public void logEvent(String message) {
        if (eventLog.size() >= MAX_ENTRIES) eventLog.pollFirst();
        eventLog.addLast(LocalDateTime.now() + " | " + message);
    }

    public void logError(String message) {
        if (errorLog.size() >= MAX_ENTRIES) errorLog.pollFirst();
        errorLog.addLast(LocalDateTime.now() + " | " + message);
    }

    public List<String> getEventLog() {
        return new ArrayList<>(eventLog);
    }

    public List<String> getErrorLog() {
        return new ArrayList<>(errorLog);
    }
}
