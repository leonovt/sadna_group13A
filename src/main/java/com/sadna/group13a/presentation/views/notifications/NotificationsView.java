package com.sadna.group13a.presentation.views.notifications;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.infrastructure.UserNotification;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Route("notifications")
@PageTitle("Notifications")
public class NotificationsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final NotificationsPresenter presenter;
    private final ListDataProvider<UserNotification> dataProvider =
            DataProvider.ofCollection(new ArrayList<>());
    private final Span errorSpan = new Span();
    private final Span emptySpan = new Span("No notifications yet.");

    public NotificationsView(NotificationsPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (!presenter.isTokenValid(token)) {
            event.rerouteTo(LoginView.class);
            return;
        }
        String userId = presenter.getUserId(token);
        List<UserNotification> notifications = presenter.getNotifications(userId);
        dataProvider.getItems().clear();
        dataProvider.getItems().addAll(notifications);
        dataProvider.refreshAll();
        emptySpan.setVisible(notifications.isEmpty());
    }

    private void initView() {
        setSizeFull();
        setPadding(true);

        errorSpan.getStyle().set("color", "var(--lumo-error-color)");
        errorSpan.setVisible(false);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        header.addAndExpand(new H2("Notifications"));

        Grid<UserNotification> grid = new Grid<>();
        grid.setDataProvider(dataProvider);
        grid.setWidthFull();

        grid.addColumn(UserNotification::message)
                .setHeader("Message")
                .setAutoWidth(true)
                .setFlexGrow(1);

        grid.addColumn(n -> n.createdAt() != null ? n.createdAt().format(DATE_FMT) : "")
                .setHeader("Received")
                .setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(this::buildActions))
                .setHeader("Actions")
                .setAutoWidth(true);

        add(header, emptySpan, errorSpan, grid);
    }

    private HorizontalLayout buildActions(UserNotification notification) {
        HorizontalLayout actions = new HorizontalLayout();
        if (UserNotification.TYPE_STAFF_NOMINATION.equals(notification.type())) {
            Button accept = new Button("Accept", e -> handleAccept(notification));
            Button decline = new Button("Decline", e -> handleDecline(notification));
            accept.getStyle().set("background-color", "var(--lumo-success-color)").set("color", "white");
            decline.getStyle().set("background-color", "var(--lumo-error-color)").set("color", "white");
            actions.add(accept, decline);
        } else {
            actions.add(new Button("Dismiss", e -> handleDismiss(notification)));
        }
        return actions;
    }

    private void handleAccept(UserNotification notification) {
        String token = presenter.getToken();
        Result<Void> result = presenter.acceptNomination(token, notification.metadata());
        if (result.isSuccess()) {
            removeRow(notification);
            showError(null);
        } else {
            showError("Failed to accept nomination: " + result.getErrorMessage());
        }
    }

    private void handleDecline(UserNotification notification) {
        String token = presenter.getToken();
        Result<Void> result = presenter.rejectNomination(token, notification.metadata());
        if (result.isSuccess()) {
            removeRow(notification);
            showError(null);
        } else {
            showError("Failed to decline nomination: " + result.getErrorMessage());
        }
    }

    private void handleDismiss(UserNotification notification) {
        presenter.dismiss(notification.id());
        removeRow(notification);
    }

    private void removeRow(UserNotification notification) {
        dataProvider.getItems().remove(notification);
        dataProvider.refreshAll();
        emptySpan.setVisible(dataProvider.getItems().isEmpty());
    }

    private void showError(String message) {
        if (message == null || message.isBlank()) {
            errorSpan.setVisible(false);
        } else {
            errorSpan.setText(message);
            errorSpan.setVisible(true);
        }
    }
}
