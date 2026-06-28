package com.sadna.group13a.presentation.views.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.DTO.PaymentDetails;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CheckoutPresenter {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CheckoutPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAccess() {
        return getToken() != null;
    }

    public void loadCart(CheckoutView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<OrderDTO> result = orderService.viewCart(token);
        if (result.isSuccess()) {
            OrderDTO cart = result.getData().orElse(null);
            if (cart != null) {
                view.displayCart(cart);
            } else {
                view.showError("No active cart. Please add items before checking out.");
            }
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleRemoveItem(String eventId, String zoneId, String seatId, CheckoutView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = orderService.removeItemFromCart(token, eventId, zoneId, seatId);
        if (result.isSuccess()) {
            loadCart(view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCheckout(String orderId, String raffleAuthCode, List<String> couponCodes, PaymentDetails paymentDetails, CheckoutView view) {
        if (paymentDetails == null) {
            view.showError("Please enter your card details.");
            return;
        }

        String rawCard = paymentDetails.cardNumber() == null ? "" : paymentDetails.cardNumber().replaceAll("\\s", "");
        if (!rawCard.matches("\\d{16}")) {
            view.showError("Card number must be exactly 16 digits.");
            return;
        }

        String month = paymentDetails.month() == null ? "" : paymentDetails.month().trim();
        if (!month.matches("^(0[1-9]|1[0-2])$")) {
            view.showError("Expiry month must be MM (01–12).");
            return;
        }

        String year = paymentDetails.year() == null ? "" : paymentDetails.year().trim();
        if (!year.matches("\\d{4}")) {
            view.showError("Expiry year must be YYYY (e.g. 2026).");
            return;
        }

        YearMonth expiry = YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
        if (expiry.isBefore(YearMonth.now())) {
            view.showError("Card expiry date is in the past.");
            return;
        }

        String cvv = paymentDetails.cvv() == null ? "" : paymentDetails.cvv().trim();
        if (!cvv.matches("\\d{3,4}")) {
            view.showError("CVV must be 3 or 4 digits.");
            return;
        }

        if (paymentDetails.holder() == null || paymentDetails.holder().isBlank()) {
            view.showError("Please enter the cardholder name.");
            return;
        }

        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        PaymentDetails normalized = new PaymentDetails(
                rawCard, month, year, paymentDetails.holder(), cvv,
                paymentDetails.id(), paymentDetails.currency());
        String paymentJson;
        try {
            paymentJson = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            view.showError("Could not process payment details.");
            return;
        }
        String raffle = (raffleAuthCode == null || raffleAuthCode.isBlank()) ? null : raffleAuthCode.trim();
        List<String> coupons = (couponCodes == null) ? List.of() :
                couponCodes.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).collect(Collectors.toList());
        Result<OrderHistoryDTO> result = orderService.executeCheckout(token, orderId, raffle, coupons, paymentJson);
        if (result.isSuccess()) {
            OrderHistoryDTO receipt = result.getData().orElse(null);
            if (receipt != null) {
                view.displayReceipt(receipt);
            }
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCancelCart(CheckoutView view) {
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        Result<Void> result = orderService.cancelCart(token);
        if (result.isSuccess()) {
            UI.getCurrent().navigate("");
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
