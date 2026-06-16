package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.RaffleDTO;
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

import java.util.List;

@Route("company/:companyId/raffles")
@PageTitle("Raffle Management")
public class CompanyRaffleView extends VerticalLayout implements BeforeEnterObserver {

    private final CompanyRafflePresenter presenter;

    private String companyId;

    private final Span statusMessage = new Span();
    private final Grid<RaffleDTO> raffleGrid = new Grid<>(RaffleDTO.class, false);

    // ── Create raffle ─────────────────────────────────────────────
    private final TextField createEventIdField = new TextField("Event ID");

    // ── Selected raffle actions ───────────────────────────────────
    private final TextField raffleIdField      = new TextField("Raffle ID");
    private final TextField winnersCountField  = new TextField("Number of winners");
    private final TextField validMinutesField  = new TextField("Code valid (minutes)");

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
        presenter.handleLoadCompanyRaffles(companyId, this);
    }

    private void initView() {
        removeAll();
        add(new Button("<- Back", e -> UI.getCurrent().navigate("company/" + companyId)));
        setPadding(true);
        setSpacing(true);

        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Raffle list grid ──────────────────────────────────────
        raffleGrid.addColumn(RaffleDTO::id).setHeader("Raffle ID").setAutoWidth(true);
        raffleGrid.addColumn(RaffleDTO::eventId).setHeader("Event").setAutoWidth(true);
        raffleGrid.addColumn(r -> r.status().name()).setHeader("Status").setAutoWidth(true);
        raffleGrid.addColumn(RaffleDTO::totalParticipants).setHeader("Participants").setAutoWidth(true);
        raffleGrid.setHeight("220px");
        raffleGrid.addSelectionListener(sel ->
                sel.getFirstSelectedItem().ifPresent(r -> raffleIdField.setValue(r.id())));

        // ── Create raffle ─────────────────────────────────────────
        Button createBtn = new Button("Create Raffle", e -> {
            clearFeedback();
            presenter.handleCreateRaffle(createEventIdField.getValue(), companyId, this);
        });
        HorizontalLayout createRow = new HorizontalLayout(createEventIdField, createBtn);
        createRow.setAlignItems(Alignment.BASELINE);

        // ── Actions on selected raffle ────────────────────────────
        raffleIdField.setReadOnly(false);
        Button closeBtn = new Button("Close Raffle", e -> {
            clearFeedback();
            presenter.handleCloseRaffle(raffleIdField.getValue(), companyId, this);
        });
        Button detailsBtn = new Button("View Details", e -> {
            clearFeedback();
            presenter.handleViewDetails(raffleIdField.getValue(), this);
        });
        HorizontalLayout closeRow = new HorizontalLayout(raffleIdField, closeBtn, detailsBtn);
        closeRow.setAlignItems(Alignment.BASELINE);

        winnersCountField.setPlaceholder("e.g. 10");
        winnersCountField.setWidth("10rem");
        validMinutesField.setPlaceholder("e.g. 60");
        validMinutesField.setWidth("10rem");
        Button drawBtn = new Button("Draw Winners", e -> {
            clearFeedback();
            presenter.handleDrawWinners(
                    raffleIdField.getValue(),
                    companyId,
                    winnersCountField.getValue(),
                    validMinutesField.getValue(),
                    this);
        });
        Button refreshBtn = new Button("Refresh", e -> {
            clearFeedback();
            presenter.handleLoadCompanyRaffles(companyId, this);
        });
        HorizontalLayout drawRow = new HorizontalLayout(winnersCountField, validMinutesField, drawBtn, refreshBtn);
        drawRow.setAlignItems(Alignment.BASELINE);

        add(
            new H2("Raffle Management"),
            statusMessage,
            new H3("Company Raffles"), raffleGrid,
            new H3("Create Raffle"), createRow,
            new H3("Selected Raffle Actions"),
            closeRow,
            new H3("Draw Winners"), drawRow
        );
    }

    public void showRaffleList(List<RaffleDTO> raffles) {
        raffleGrid.setItems(raffles);
    }

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
        statusMessage.setText(
            "Raffle ID: " + raffle.id() +
            " | Event: " + raffle.eventId() +
            " | Status: " + raffle.status() +
            " | Participants: " + raffle.totalParticipants());
        statusMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
        statusMessage.setVisible(true);
    }

    private void clearFeedback() {
        statusMessage.setVisible(false);
    }
}
