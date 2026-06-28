package com.sadna.group13a.presentation.views.cart;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("cart")
@PageTitle("Cart")
public class CartView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CartPresenter presenter;

    private final Span statusMessage = new Span();
    private final Span emptyMessage = new Span("Your cart is empty.");
    private final VerticalLayout cartSection = new VerticalLayout();
    private final Grid<OrderItemDTO> itemsGrid = new Grid<>(OrderItemDTO.class, false);
    private final Span totalLabel = new Span();
    private final Span expiryLabel = new Span();

    public CartView(CartPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!presenter.hasAccess()) {
            event.forwardTo(LoginView.class);
            return;
        }
        presenter.loadCart(this);
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
        header.add(new H2("Cart"));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

        // ── Empty state ───────────────────────────────────────────
        emptyMessage.setVisible(false);
        emptyMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");

        // ── Items grid ────────────────────────────────────────────
        itemsGrid.addColumn(OrderItemDTO::eventId).setHeader("Event ID").setFlexGrow(2);
        itemsGrid.addColumn(OrderItemDTO::zoneId).setHeader("Zone").setFlexGrow(1);
        itemsGrid.addColumn(item -> item.seatId() != null ? item.seatId() : "—").setHeader("Seat");
        itemsGrid.addColumn(item -> String.format("$%.2f", item.basePrice())).setHeader("Price");
        itemsGrid.addComponentColumn(item -> {
            Button removeBtn = new Button("Remove", e -> {
                statusMessage.setVisible(false);
                presenter.handleRemoveItem(item.eventId(), item.zoneId(), item.seatId(), this);
            });
            removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            return removeBtn;
        }).setHeader("").setFlexGrow(0);
        itemsGrid.setWidthFull();
        itemsGrid.setAllRowsVisible(true);

        totalLabel.getStyle().set("font-weight", "bold").set("font-size", "1.2rem");
        expiryLabel.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        Button checkoutBtn = new Button("Proceed to Checkout",
                e -> getUI().ifPresent(ui -> ui.navigate(CheckoutView.class)));
        checkoutBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel Cart", e -> {
            statusMessage.setVisible(false);
            presenter.handleCancelCart(this);
        });
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(checkoutBtn, cancelBtn);
        actions.setAlignItems(Alignment.BASELINE);

        cartSection.setPadding(false);
        cartSection.add(itemsGrid, expiryLabel, totalLabel, actions);
        cartSection.setVisible(false);

        add(header, statusMessage, emptyMessage, new H3("Your Items"), cartSection);
    }

    // ── View callbacks ────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void displayCart(OrderDTO cart) {
        emptyMessage.setVisible(false);
        itemsGrid.setItems(cart.items() == null ? List.of() : cart.items());
        totalLabel.setText(String.format("Total: $%.2f", cart.totalBasePrice()));
        if (cart.expiresAt() != null) {
            expiryLabel.setText("Cart expires: " + cart.expiresAt().format(DATE_FORMAT));
            expiryLabel.setVisible(true);
        } else {
            expiryLabel.setVisible(false);
        }
        cartSection.setVisible(true);
        statusMessage.setVisible(false);
    }

    public void displayEmpty() {
        cartSection.setVisible(false);
        emptyMessage.setVisible(true);
        statusMessage.setVisible(false);
    }
}
