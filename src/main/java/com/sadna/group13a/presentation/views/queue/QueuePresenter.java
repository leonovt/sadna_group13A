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
     * Entering the waiting room joins the queue: the user is either admitted
     * immediately, placed in line, or — when no queue is configured — granted
     * direct access. The resulting standing is rendered.
     */
    public void onEnter(String eventId, QueueView view) {
        String token = requireToken(view);
        if (token == null || isBlankEvent(eventId, view)) {
            return;
        }

        Result<QueueStatusDTO> result = queueService.joinQueue(token, eventId);
        if (result.isSuccess()) {
            view.showStatus(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
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
        if (token == null) {
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
