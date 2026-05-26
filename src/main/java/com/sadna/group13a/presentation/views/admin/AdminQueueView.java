package com.sadna.group13a.presentation.views.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.domain.Aggregates.TicketQueue.TicketQueue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

@Route("admin/queues")
@PageTitle("Queue Control")
public class AdminQueueView extends VerticalLayout {

    private final AdminQueuePresenter presenter;

    private final Span statusMessage = new Span();
    private final TextField eventIdField  = new TextField("Event ID");
    private final TextField newMaxField   = new TextField("New max users");
    private final Grid<TicketQueue> queueGrid = new Grid<>(TicketQueue.class, false);

    public AdminQueueView(AdminQueuePresenter presenter) {
        this.presenter = presenter;
        initView();
        addAttachListener(e -> presenter.loadQueues(this));
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Queue Control"), new RouterLink("← Back to Dashboard", AdminDashboardView.class));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Queue grid ────────────────────────────────────────────
        queueGrid.addColumn(TicketQueue::getEventId).setHeader("Event ID").setFlexGrow(2);
        queueGrid.addColumn(TicketQueue::getMaxConcurrentUsers).setHeader("Max Capacity");
        queueGrid.addColumn(TicketQueue::getActiveCount).setHeader("Active");
        queueGrid.addColumn(TicketQueue::getWaitingCount).setHeader("Waiting");
        queueGrid.setWidthFull();
        queueGrid.setMaxHeight("350px");

        // ── Actions ───────────────────────────────────────────────
        newMaxField.setPlaceholder("e.g. 10");
        newMaxField.setWidth("10rem");

        Button clearBtn = new Button("Clear Queue", e -> {
            statusMessage.setVisible(false);
            presenter.handleClearQueue(eventIdField.getValue(), this);
        });

        Button adjustBtn = new Button("Adjust Capacity", e -> {
            statusMessage.setVisible(false);
            presenter.handleAdjustCapacity(eventIdField.getValue(), newMaxField.getValue(), this);
        });

        HorizontalLayout actions = new HorizontalLayout(eventIdField, clearBtn, newMaxField, adjustBtn);
        actions.setAlignItems(Alignment.BASELINE);

        add(
                header,
                statusMessage,
                new H3("All Queues"), queueGrid,
                new H3("Queue Actions"), actions
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

    public void displayQueues(List<TicketQueue> queues) {
        queueGrid.setItems(queues);
    }
}
