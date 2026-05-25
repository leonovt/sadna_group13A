package com.sadna.group13a.presentation.views.queue;

import com.sadna.group13a.application.DTO.QueueStatusDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;

@Route("queue/:eventId")
@PageTitle("Waiting Room")
public class QueueView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final QueuePresenter presenter;
    private String eventId;

    private final Span statusMessage = new Span();
    private final Span positionLabel = new Span();
    private final Span waitingLabel = new Span();
    private final Span expiryLabel = new Span();
    private final Span errorMessage = new Span();
    private final Button proceedButton = new Button("Proceed to Purchase");
    private final Button refreshButton = new Button("Refresh Status");
    private final Button leaveButton = new Button("Leave Queue");

    public QueueView(QueuePresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        eventId = event.getRouteParameters().get("eventId").orElse("");
        initView();
        presenter.onEnter(eventId, this);
    }

    private void initView() {
        removeAll();
        setAlignItems(Alignment.CENTER);
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        proceedButton.setVisible(false);
        proceedButton.addClickListener(e -> presenter.proceedToPurchase(eventId));

        refreshButton.addClickListener(e -> presenter.refresh(eventId, this));
        leaveButton.addClickListener(e -> presenter.leave(eventId, this));

        HorizontalLayout actions = new HorizontalLayout(refreshButton, proceedButton, leaveButton);

        add(
            new H2("Waiting Room"),
            new Span("Event: " + eventId),
            statusMessage,
            positionLabel,
            waitingLabel,
            expiryLabel,
            errorMessage,
            actions
        );
    }

    /**
     * Renders the user's current standing. The DTO encodes three cases:
     * active (it's the user's turn), waiting (a non-negative position in line),
     * or not in the queue (a negative position, reported by a status query).
     */
    public void showStatus(QueueStatusDTO status) {
        errorMessage.setVisible(false);

        if (status.isActive()) {
            statusMessage.setText("It's your turn — you may now purchase tickets.");
            positionLabel.setVisible(false);
            waitingLabel.setText("People waiting behind you: " + status.totalWaiting());
            waitingLabel.setVisible(true);
            if (status.accessExpiresAt() != null) {
                expiryLabel.setText("Access valid until: " + status.accessExpiresAt().format(EXPIRY_FORMAT));
                expiryLabel.setVisible(true);
            } else {
                expiryLabel.setVisible(false);
            }
            proceedButton.setVisible(true);
        } else if (status.positionInLine() < 0) {
            statusMessage.setText("You are not currently in the queue for this event.");
            positionLabel.setVisible(false);
            waitingLabel.setVisible(false);
            expiryLabel.setVisible(false);
            proceedButton.setVisible(false);
        } else {
            statusMessage.setText("You are in the waiting line. Keep this page open and refresh to track your turn.");
            positionLabel.setText("Your position in line: " + status.positionInLine());
            positionLabel.setVisible(true);
            waitingLabel.setText("Total people waiting: " + status.totalWaiting());
            waitingLabel.setVisible(true);
            expiryLabel.setVisible(false);
            proceedButton.setVisible(false);
        }
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}
