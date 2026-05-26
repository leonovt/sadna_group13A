package com.sadna.group13a.presentation.views.queue;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.QueueService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class QueuePresenter {

    private final QueueService queueService;

    public QueuePresenter(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Entering the waiting room renders the user's standing. We first read the
     * current status: if the user is already in the queue (active, or holding a
     * waiting position) we simply refresh — navigating back to a page you are
     * already queued for must not re-join. Only a user who is not yet in the
     * queue is joined, at which point they are either admitted immediately,
     * placed in line, or — when no queue is configured — granted direct access.
     */
    public void onEnter(String eventId, QueueView view) {
        String token = requireToken(view);
        if (token == null || isBlankEvent(eventId, view)) {
            return;
        }

        Result<QueueStatusDTO> statusResult = queueService.getStatus(token, eventId);
        if (statusResult.isSuccess() && isInQueue(statusResult.getOrThrow())) {
            view.showStatus(statusResult.getOrThrow());
            return;
        }

        Result<QueueStatusDTO> joinResult = queueService.joinQueue(token, eventId);
        if (joinResult.isSuccess()) {
            view.showStatus(joinResult.getOrThrow());
        } else {
            view.showError(joinResult.getErrorMessage());
        }
    }

    /**
     * A user counts as already in the queue when they hold active access (which
     * also covers the no-queue direct-access case) or occupy a waiting position
     * ({@code positionInLine >= 0}); {@code getStatus} reports a position of -1
     * for someone who is not in the queue at all.
     */
    private boolean isInQueue(QueueStatusDTO status) {
        return status.isActive() || status.positionInLine() >= 0;
    }

    /** Re-reads the user's position without altering the queue. */
    public void refresh(String eventId, QueueView view) {
        String token = requireToken(view);
        if (token == null || isBlankEvent(eventId, view)) {
            return;
        }

        Result<QueueStatusDTO> result = queueService.getStatus(token, eventId);
        if (result.isSuccess()) {
            view.showStatus(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    /** Releases the user's slot (freeing it for the next person) and returns home. */
    public void leave(String eventId, QueueView view) {
        String token = requireToken(view);
        if (token == null || isBlankEvent(eventId, view)) {
            return;
        }

        Result<Void> result = queueService.releaseAccess(token, eventId);
        if (result.isSuccess()) {
            UI.getCurrent().navigate("");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    /** Sends an admitted user to the event page to complete their purchase. */
    public void proceedToPurchase(String eventId) {
        UI.getCurrent().navigate("events/" + eventId);
    }

    private String requireToken(QueueView view) {
        Object token = VaadinSession.getCurrent().getAttribute("token");
        // A non-String value (corrupted session, future refactor) is treated the
        // same as a missing token rather than throwing a ClassCastException.
        if (!(token instanceof String)) {
            view.showError("You must be logged in to join a queue.");
            return null;
        }
        return (String) token;
    }

    private boolean isBlankEvent(String eventId, QueueView view) {
        if (eventId == null || eventId.isBlank()) {
            view.showError("No event was specified for this queue.");
            return true;
        }
        return false;
    }
}
