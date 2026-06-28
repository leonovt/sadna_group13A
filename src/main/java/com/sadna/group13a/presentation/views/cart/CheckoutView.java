package com.sadna.group13a.presentation.views.cart;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.OrderHistoryItemDTO;
import com.sadna.group13a.application.DTO.OrderItemDTO;
import com.sadna.group13a.application.DTO.PaymentDetails;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.sadna.group13a.presentation.views.home.HomeView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
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
import java.util.ArrayList;
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
    // ── Card details (sent to the external payment service) ──────────
    private final TextField cardNumberField = new TextField("Card Number");
    private final TextField holderField = new TextField("Cardholder Name");
    private final TextField monthField = new TextField("Exp. Month");
    private final TextField yearField = new TextField("Exp. Year");
    private final TextField cvvField = new TextField("CVV");
    private final TextField idField = new TextField("ID Number");
    private final TextField currencyField = new TextField("Currency");
    private final TextField couponInputField = new TextField("Coupon Code");
    private final Button applyCouponBtn = new Button("Apply Coupon");
    private final List<String> appliedCouponCodes = new ArrayList<>();
    private final VerticalLayout couponChipsLayout = new VerticalLayout();
    private final TextField raffleAuthCodeField = new TextField("Raffle Authorization Code");

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

        // ── Payment form ──────────────────────────────────────────
        cardNumberField.setPlaceholder("1234 5678 9012 3456");
        cardNumberField.setMaxLength(19);
        cardNumberField.setPattern("[0-9 ]*");
        cardNumberField.addValueChangeListener(e -> {
            String digits = e.getValue().replaceAll("[^0-9]", "");
            if (digits.length() > 16) digits = digits.substring(0, 16);
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0 && i % 4 == 0) formatted.append(' ');
                formatted.append(digits.charAt(i));
            }
            String result = formatted.toString();
            if (!result.equals(e.getValue())) {
                cardNumberField.setValue(result);
            }
        });

        monthField.setPlaceholder("MM");
        monthField.setMaxLength(2);
        monthField.setPattern("[0-9]*");
        yearField.setPlaceholder("YYYY");
        yearField.setMaxLength(4);
        yearField.setPattern("[0-9]*");
        cvvField.setPlaceholder("3–4 digits");
        cvvField.setMaxLength(4);
        cvvField.setPattern("[0-9]*");
        idField.setPlaceholder("Cardholder ID");
        currencyField.setValue("USD");

        FormLayout cardForm = new FormLayout(
                cardNumberField, holderField, monthField, yearField, cvvField, idField, currencyField);
        cardForm.setWidthFull();

        couponInputField.setPlaceholder("Enter coupon code");
        couponInputField.setWidthFull();
        couponChipsLayout.setPadding(false);
        couponChipsLayout.setSpacing(false);
        applyCouponBtn.addClickListener(e -> {
            String val = couponInputField.getValue().trim();
            if (!val.isBlank() && !appliedCouponCodes.contains(val)) {
                appliedCouponCodes.add(val);
                refreshCouponChips();
                couponInputField.clear();
            }
        });

        raffleAuthCodeField.setPlaceholder("Enter your raffle authorization code");
        raffleAuthCodeField.setWidthFull();
        raffleAuthCodeField.setVisible(false);

        Button placeOrderBtn = new Button("Place Order", e -> {
            statusMessage.setVisible(false);
            PaymentDetails details = new PaymentDetails(
                    cardNumberField.getValue(), monthField.getValue(), yearField.getValue(),
                    holderField.getValue(), cvvField.getValue(), idField.getValue(), currencyField.getValue());
            presenter.handleCheckout(currentOrderId,
                    raffleAuthCodeField.getValue(),
                    new ArrayList<>(appliedCouponCodes),
                    details, this);
        });
        placeOrderBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel Cart", e -> {
            statusMessage.setVisible(false);
            presenter.handleCancelCart(this);
        });
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(placeOrderBtn, cancelBtn);
        actions.setAlignItems(Alignment.BASELINE);

        HorizontalLayout couponRow = new HorizontalLayout(couponInputField, applyCouponBtn);
        couponRow.setAlignItems(Alignment.BASELINE);
        couponRow.setWidthFull();

        cartSection.setPadding(false);
        cartSection.add(
                itemsGrid, expiryLabel, totalLabel,
                new H3("Payment"), cardForm,
                couponRow, couponChipsLayout, raffleAuthCodeField,
                actions
        );

        // ── Receipt section ───────────────────────────────────────
        receiptSection.setPadding(false);
        receiptSection.setVisible(false);

        add(header, statusMessage, new H3("Your Items"), cartSection, receiptSection);
    }

    // ── View callbacks ────────────────────────────────────────────

    private void refreshCouponChips() {
        couponChipsLayout.removeAll();
        for (String code : appliedCouponCodes) {
            HorizontalLayout chip = new HorizontalLayout();
            chip.setAlignItems(Alignment.CENTER);
            chip.setSpacing(false);
            chip.getStyle()
                    .set("background", "var(--lumo-primary-color-10pct)")
                    .set("border-radius", "1em")
                    .set("padding", "0.2em 0.6em")
                    .set("margin", "0.2em");
            Span codeLabel = new Span(code);
            Button removeBtn = new Button("×", ev -> {
                appliedCouponCodes.remove(code);
                refreshCouponChips();
            });
            removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            chip.add(codeLabel, removeBtn);
            couponChipsLayout.add(chip);
        }
    }

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
        raffleAuthCodeField.setVisible(cart.hasRaffleEvent());
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
