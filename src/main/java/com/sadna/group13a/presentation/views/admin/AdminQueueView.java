package com.sadna.group13a.presentation.views.admin;

import com.sadna.group13a.application.DTO.TicketQueueDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

@Route("admin/queues")
@PageTitle("Queue Control")
public class AdminQueueView extends VerticalLayout implements BeforeEnterObserver {

    private final AdminQueuePresenter presenter;

    private final Span statusMessage = new Span();
    private final TextField eventIdField  = new TextField("Event ID");
    private final TextField newMaxField   = new TextField("New max users");
    private final Grid<TicketQueueDTO> queueGrid = new Grid<>(TicketQueueDTO.class, false);

    public AdminQueueView(AdminQueuePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    // Issues #4 & #5 fix: gate access and load data before the view is rendered
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAdminAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        presenter.loadQueues(this);
    }

    private void initView() {
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
        // Issue #6 fix: bound to TicketQueueDTO, not the domain aggregate
        queueGrid.addColumn(TicketQueueDTO::eventId).setHeader("Event ID").setFlexGrow(2);
        queueGrid.addColumn(TicketQueueDTO::maxConcurrentUsers).setHeader("Max Capacity");
        queueGrid.addColumn(TicketQueueDTO::activeCount).setHeader("Active");
        queueGrid.addColumn(TicketQueueDTO::waitingCount).setHeader("Waiting");
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

    public void displayQueues(List<TicketQueueDTO> queues) {
        queueGrid.setItems(queues);
    }
}
