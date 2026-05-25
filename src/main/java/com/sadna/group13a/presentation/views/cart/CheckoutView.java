package com.sadna.group13a.presentation.views.cart;

import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.sadna.group13a.presentation.views.home.HomeView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.router.RouterLink;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route("checkout")
@PageTitle("Checkout")
public class CheckoutView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CheckoutPresenter presenter;

    private final Span statusMessage = new Span();

    // ── Cart section ──────────────────────────────────────────────
    private final VerticalLayout cartSection = new VerticalLayout();
    private final Grid<OrderItemDTO> itemsGrid = new Grid<>(OrderItemDTO.class, false);
    private final Span totalLabel = new Span();
    private final Span expiryLabel = new Span();
    private final TextField paymentField = new TextField("Payment Details");
    private final TextField authCodeField = new TextField("Auth Code");

    // ── Receipt section ───────────────────────────────────────────
    private final VerticalLayout receiptSection = new VerticalLayout();

    private String currentOrderId;

    public CheckoutView(CheckoutPresenter presenter) {
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
        setPadding(true);
        setSpacing(true);

        // ── Header ────────────────────────────────────────────────
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H2("Checkout"), new RouterLink("← Back to Home", HomeView.class));

        // ── Status message ────────────────────────────────────────
        statusMessage.setVisible(false);
        statusMessage.getStyle().set("font-weight", "bold");

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
        expiryLabel.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        // ── Payment form ──────────────────────────────────────────
        paymentField.setWidthFull();
        paymentField.setPlaceholder("e.g. 4111 1111 1111 1111");
        authCodeField.setWidthFull();
        authCodeField.setPlaceholder("Leave blank for regular / queue sales");
        authCodeField.setHelperText("Required for raffle sales only");

        Button placeOrderBtn = new Button("Place Order", e -> {
            statusMessage.setVisible(false);
            presenter.handleCheckout(currentOrderId, authCodeField.getValue(), paymentField.getValue(), this);
        });
        placeOrderBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel Cart", e -> {
            statusMessage.setVisible(false);
            presenter.handleCancelCart(this);
        });
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(placeOrderBtn, cancelBtn);
        actions.setAlignItems(Alignment.BASELINE);

        cartSection.setPadding(false);
        cartSection.add(
                itemsGrid, expiryLabel, totalLabel,
                new H3("Payment"), paymentField, authCodeField,
                actions
        );

        // ── Receipt section ───────────────────────────────────────
        receiptSection.setPadding(false);
        receiptSection.setVisible(false);

        add(header, statusMessage, new H3("Your Items"), cartSection, receiptSection);
    }

    // ── View callbacks ────────────────────────────────────────────

    public void showError(String message) {
        statusMessage.setText(message);
        statusMessage.getStyle().set("color", "var(--lumo-error-color)");
        statusMessage.setVisible(true);
    }

    public void displayCart(OrderDTO cart) {
        currentOrderId = cart.orderId();
        itemsGrid.setItems(cart.items() == null ? List.of() : cart.items());
        totalLabel.setText(String.format("Total: $%.2f", cart.totalBasePrice()));
        if (cart.expiresAt() != null) {
            expiryLabel.setText("Cart expires: " + cart.expiresAt().format(DATE_FORMAT));
            expiryLabel.setVisible(true);
        } else {
            expiryLabel.setVisible(false);
        }
        cartSection.setVisible(true);
        receiptSection.setVisible(false);
        statusMessage.setVisible(false);
    }

    public void displayReceipt(OrderHistoryDTO receipt) {
        cartSection.setVisible(false);
        receiptSection.setVisible(true);
        receiptSection.removeAll();

        Span receiptId = new Span("Receipt ID: " + receipt.receiptId());
        receiptId.getStyle().set("font-weight", "bold");

        Span paidOn = new Span(String.format("Total paid: $%.2f  •  %s",
                receipt.totalPaid(),
                receipt.purchaseDate() != null ? receipt.purchaseDate().format(DATE_FORMAT) : "—"));

        Grid<OrderHistoryItemDTO> receiptGrid = new Grid<>(OrderHistoryItemDTO.class, false);
        receiptGrid.addColumn(OrderHistoryItemDTO::eventTitle).setHeader("Event").setFlexGrow(2);
        receiptGrid.addColumn(i -> i.eventDate() != null ? i.eventDate().format(DATE_FORMAT) : "—").setHeader("Date");
        receiptGrid.addColumn(OrderHistoryItemDTO::companyName).setHeader("Company");
        receiptGrid.addColumn(OrderHistoryItemDTO::zoneName).setHeader("Zone");
        receiptGrid.addColumn(OrderHistoryItemDTO::seatLabel).setHeader("Seat");
        receiptGrid.addColumn(i -> String.format("$%.2f", i.pricePaid())).setHeader("Price paid");
        receiptGrid.setItems(receipt.items() == null ? List.of() : receipt.items());
        receiptGrid.setAllRowsVisible(true);
        receiptGrid.setWidthFull();

        receiptSection.add(
                new H3("Order Confirmed"),
                receiptId, paidOn,
                receiptGrid,
                new RouterLink("← Back to Home", HomeView.class)
        );
    }
}
