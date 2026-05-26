package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;

@Route("member/raffles")
@PageTitle("Raffles")
public class RaffleView extends VerticalLayout {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RafflePresenter presenter;

    private final TextField raffleIdField = new TextField("Raffle ID");
    private final Span errorMessage = new Span();
    private final Span infoMessage = new Span();
    private final VerticalLayout detailsLayout = new VerticalLayout();

    public RaffleView(RafflePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setAlignItems(Alignment.CENTER);
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        infoMessage.getStyle().set("color", "var(--lumo-success-color)");
        infoMessage.setVisible(false);

        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.setVisible(false);

        raffleIdField.setWidthFull();

        Button joinButton = new Button("Join Raffle", e -> {
            clearFeedback();
            presenter.handleJoinRaffle(raffleIdField.getValue(), this);
        });

        Button detailsButton = new Button("View Details", e -> {
            clearFeedback();
            presenter.handleViewDetails(raffleIdField.getValue(), this);
        });

        Button resultButton = new Button("Check My Result", e -> {
            clearFeedback();
            presenter.handleCheckResult(raffleIdField.getValue(), this);
        });

        HorizontalLayout actions = new HorizontalLayout(joinButton, detailsButton, resultButton);

        add(new H2("Raffles"), raffleIdField, actions, errorMessage, infoMessage, detailsLayout);
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }

    public void showInfo(String message) {
        infoMessage.setText(message);
        infoMessage.setVisible(true);
    }

    public void showRaffleDetails(RaffleDTO raffle) {
        detailsLayout.removeAll();
        detailsLayout.add(
            new H3("Raffle Details"),
            new Span("Status: " + raffle.status()),
            new Span("Participants: " + raffle.totalParticipants())
        );
        detailsLayout.setVisible(true);
    }

    public void showWinningTicket(WinningTicketDTO ticket) {
        detailsLayout.removeAll();
        detailsLayout.add(
            new H3("Congratulations — you won!"),
            new Span("Event: " + ticket.eventId()),
            new Span("Authorization code: " + ticket.authorizationCode()),
            new Span("Valid until: " + ticket.expiresAt().format(EXPIRY_FORMAT))
        );
        detailsLayout.setVisible(true);
    }

    private void clearFeedback() {
        errorMessage.setVisible(false);
        infoMessage.setVisible(false);
        detailsLayout.removeAll();
        detailsLayout.setVisible(false);
    }
}
