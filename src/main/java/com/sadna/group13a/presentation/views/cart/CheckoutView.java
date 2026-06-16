package com.sadna.group13a.presentation.views.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.application.DTO.PaymentDetailsDTO;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.sadna.group13a.presentation.views.home.HomeView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CheckoutPresenter presenter;

    private final Span statusMessage = new Span();

    // ── Cart section ──────────────────────────────────────────────
    private final VerticalLayout cartSection = new VerticalLayout();
    private final Grid<OrderItemDTO> itemsGrid = new Grid<>(OrderItemDTO.class, false);
    private final Span totalLabel = new Span();
    private final Span expiryLabel = new Span();

    // Card details — maps 1:1 to the WSEP external API fields
    private final TextField   cardNumberField = new TextField("Card Number");
    private final TextField   expiryMonthField = new TextField("Month (MM)");
    private final TextField   expiryYearField  = new TextField("Year (YY)");
    private final TextField   holderField      = new TextField("Cardholder Name");
    private final PasswordField cvvField       = new PasswordField("CVV");
    private final TextField   holderIdField    = new TextField("Holder ID");
    private final TextField   currencyField    = new TextField("Currency");

    private final TextField authCodeField = new TextField("Authorization / Coupon Code");

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
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
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

        // ── Card detail fields ────────────────────────────────────
        cardNumberField.setPlaceholder("4111 1111 1111 1111");
        cardNumberField.setWidthFull();

        expiryMonthField.setPlaceholder("01");
        expiryYearField.setPlaceholder("27");
        HorizontalLayout expiryRow = new HorizontalLayout(expiryMonthField, expiryYearField);
        expiryRow.setWidthFull();
        expiryRow.setFlexGrow(1, expiryMonthField, expiryYearField);

        holderField.setPlaceholder("Full name as on card");
        holderField.setWidthFull();

        cvvField.setPlaceholder("123");

        holderIdField.setPlaceholder("National ID / Passport number");
        holderIdField.setWidthFull();

        currencyField.setValue("ILS");
        currencyField.setHelperText("Default: ILS");

        HorizontalLayout cvvCurrencyRow = new HorizontalLayout(cvvField, currencyField);
        cvvCurrencyRow.setWidthFull();
        cvvCurrencyRow.setFlexGrow(1, cvvField, currencyField);

        authCodeField.setWidthFull();
        authCodeField.setPlaceholder("Required for raffle events");
        authCodeField.setHelperText("Raffle winners: enter your authorization code. Other events: leave blank.");

        Button placeOrderBtn = new Button("Place Order", e -> {
            statusMessage.setVisible(false);
            submitCheckout();
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
                new H3("Payment Details"),
                cardNumberField,
                expiryRow,
                holderField,
                cvvCurrencyRow,
                holderIdField,
                authCodeField,
                actions
        );

        // ── Receipt section ───────────────────────────────────────
        receiptSection.setPadding(false);
        receiptSection.setVisible(false);

        add(header, statusMessage, new H3("Your Items"), cartSection, receiptSection);
    }

    private void submitCheckout() {
        String cardNumber = cardNumberField.getValue().replaceAll("\\s+", "");
        String month      = expiryMonthField.getValue().trim();
        String year       = expiryYearField.getValue().trim();
        String holder     = holderField.getValue().trim();
        String cvv        = cvvField.getValue().trim();
        String holderId   = holderIdField.getValue().trim();
        String currency   = currencyField.getValue().trim();

        if (cardNumber.isBlank() || month.isBlank() || year.isBlank()
                || holder.isBlank() || cvv.isBlank() || holderId.isBlank()) {
            showError("Please fill in all payment fields.");
            return;
        }
        if (currency.isBlank()) {
            currency = "ILS";
        }

        PaymentDetailsDTO details = new PaymentDetailsDTO(
                cardNumber, month, year, holder, cvv, holderId, currency);

        String paymentJson;
        try {
            paymentJson = MAPPER.writeValueAsString(details);
        } catch (Exception ex) {
            showError("Failed to prepare payment details.");
            return;
        }

        String authCode = authCodeField.getValue().trim();
        presenter.handleCheckout(currentOrderId, authCode, paymentJson, this);
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
