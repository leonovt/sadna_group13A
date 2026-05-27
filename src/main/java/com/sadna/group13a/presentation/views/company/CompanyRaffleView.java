package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
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

@Route("company/:companyId/raffles")
@PageTitle("Raffle Management")
public class CompanyRaffleView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyRafflePresenter presenter;

    private String companyId;

    private final Span statusMessage = new Span();

    // ── Create raffle ─────────────────────────────────────────────
    private final TextField createEventIdField = new TextField("Event ID");

    // ── Close / Details ───────────────────────────────────────────
    private final TextField raffleIdField = new TextField("Raffle ID");

    // ── Draw winners ──────────────────────────────────────────────
    private final TextField drawRaffleIdField    = new TextField("Raffle ID");
    private final TextField winnersCountField    = new TextField("Number of winners");
    private final TextField validMinutesField    = new TextField("Code valid (minutes)");

    // ── Details panel ─────────────────────────────────────────────
    private final VerticalLayout detailsLayout = new VerticalLayout();

    public CompanyRaffleView(CompanyRafflePresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = event.getRouteParameters().get("companyId").orElse("");
        if (!presenter.hasAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        initView();
    }

    private void initView() {
        removeAll();
        add(new Button("<- Back", e -> UI.getCurrent().navigate("company/" + companyId)));
        setPadding(true);
        setSpacing(true);

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);
        detailsLayout.setVisible(false);

        // ── Create raffle ─────────────────────────────────────────
        Button createBtn = new Button("Create Raffle", e -> {
            clearFeedback();
            presenter.handleCreateRaffle(createEventIdField.getValue(), companyId, this);
        });
        HorizontalLayout createRow = new HorizontalLayout(createEventIdField, createBtn);
        createRow.setAlignItems(Alignment.BASELINE);

        // ── Close raffle / View details ───────────────────────────
        Button closeBtn = new Button("Close Raffle", e -> {
            clearFeedback();
            presenter.handleCloseRaffle(raffleIdField.getValue(), this);
        });
        Button detailsBtn = new Button("View Details", e -> {
            clearFeedback();
            presenter.handleViewDetails(raffleIdField.getValue(), this);
        });
        HorizontalLayout raffleRow = new HorizontalLayout(raffleIdField, closeBtn, detailsBtn);
        raffleRow.setAlignItems(Alignment.BASELINE);

        // ── Draw winners ──────────────────────────────────────────
        winnersCountField.setPlaceholder("e.g. 10");
        winnersCountField.setWidth("10rem");
        validMinutesField.setPlaceholder("e.g. 60");
        validMinutesField.setWidth("10rem");

        Button drawBtn = new Button("Draw Winners", e -> {
            clearFeedback();
            presenter.handleDrawWinners(
                    drawRaffleIdField.getValue(),
                    winnersCountField.getValue(),
                    validMinutesField.getValue(),
                    this);
        });
        HorizontalLayout drawRow = new HorizontalLayout(drawRaffleIdField, winnersCountField, validMinutesField, drawBtn);
        drawRow.setAlignItems(Alignment.BASELINE);

        add(
                new H2("Raffle Management"),
                statusMessage,
                new H3("Create Raffle"), createRow,
                new H3("Close or Inspect Raffle"), raffleRow,
                detailsLayout,
                new H3("Draw Winners"), drawRow
        );
    }

    // ── View callbacks ────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void showSuccess(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-success-color)");
        statusMessage.setVisible(true);
    }

    public void showRaffleDetails(RaffleDTO raffle) {
        detailsLayout.removeAll();
        detailsLayout.add(
                new Span("Raffle ID: " + raffle.id()),
                new Span("Event: " + raffle.eventId()),
                new Span("Status: " + raffle.status()),
                new Span("Participants: " + raffle.totalParticipants())
        );
        detailsLayout.setVisible(true);
    }

    private void clearFeedback() {
        statusMessage.setVisible(false);
        detailsLayout.removeAll();
        detailsLayout.setVisible(false);
    }
}
