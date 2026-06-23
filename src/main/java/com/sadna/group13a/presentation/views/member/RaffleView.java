package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("member/raffles")
@PageTitle("Raffles")
public class RaffleView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RafflePresenter presenter;

    private final Grid<RaffleDTO> myRafflesGrid = new Grid<>(RaffleDTO.class, false);
    private final TextField raffleIdField = new TextField("Raffle ID");
    private final Span errorMessage = new Span();
    private final Span infoMessage = new Span();
    private final Span neutralMessage = new Span();
    private final VerticalLayout detailsLayout = new VerticalLayout();

    public RaffleView(RafflePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        presenter.handleLoadMyRaffles(this);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setAlignItems(Alignment.CENTER);
        setSizeFull();
        setPadding(true);

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);
        infoMessage.getStyle().set("color", "var(--lumo-success-color)");
        infoMessage.setVisible(false);
        neutralMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
        neutralMessage.setVisible(false);

        // ── My raffles grid ───────────────────────────────────────────
        myRafflesGrid.addColumn(RaffleDTO::id).setHeader("Raffle ID").setAutoWidth(true);
        myRafflesGrid.addColumn(RaffleDTO::eventId).setHeader("Event").setAutoWidth(true);
        myRafflesGrid.addColumn(r -> r.status().name()).setHeader("Status").setAutoWidth(true);
        myRafflesGrid.addColumn(RaffleDTO::totalParticipants).setHeader("Participants").setAutoWidth(true);
        myRafflesGrid.setHeight("220px");
        myRafflesGrid.addSelectionListener(sel ->
                sel.getFirstSelectedItem().ifPresent(r -> raffleIdField.setValue(r.id())));

        // ── Details / result panel ────────────────────────────────────
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.setVisible(false);

        raffleIdField.setWidthFull();

        Button joinButton = new Button("Join Raffle", e -> {
            clearFeedback();
            presenter.handleJoinRaffle(raffleIdField.getValue(), this);
        });
        Button leaveButton = new Button("Leave Raffle", e -> {
            clearFeedback();
            presenter.handleLeaveRaffle(raffleIdField.getValue(), this);
        });
        leaveButton.getStyle().set("color", "var(--lumo-error-color)");
        Button detailsButton = new Button("View Details", e -> {
            clearFeedback();
            presenter.handleViewDetails(raffleIdField.getValue(), this);
        });
        Button resultButton = new Button("Check My Result", e -> {
            clearFeedback();
            presenter.handleCheckResult(raffleIdField.getValue(), this);
        });
        Button refreshButton = new Button("Refresh", e -> {
            clearFeedback();
            presenter.handleLoadMyRaffles(this);
        });

        HorizontalLayout actions = new HorizontalLayout(joinButton, leaveButton, detailsButton, resultButton, refreshButton);

        add(
            new H2("My Raffles"),
            myRafflesGrid,
            new H3("Actions"),
            raffleIdField,
            actions,
            errorMessage, infoMessage, neutralMessage,
            detailsLayout
        );
    }

    public void showMyRaffles(List<RaffleDTO> raffles) {
        myRafflesGrid.setItems(raffles);
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }

    public void showInfo(String message) {
        infoMessage.setText(message);
        infoMessage.setVisible(true);
    }

    public void showNeutral(String message) {
        neutralMessage.setText(message);
        neutralMessage.setVisible(true);
    }

    public void showRaffleDetails(RaffleDTO raffle) {
        detailsLayout.removeAll();
        detailsLayout.add(
            new H3("Raffle Details"),
            new Span("Raffle ID: " + raffle.id()),
            new Span("Event: " + raffle.eventId()),
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
        neutralMessage.setVisible(false);
        detailsLayout.removeAll();
        detailsLayout.setVisible(false);
    }
}
