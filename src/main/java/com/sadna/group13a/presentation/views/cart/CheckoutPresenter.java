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

    public void handleCheckout(String orderId, String authCode, PaymentDetails paymentDetails, CheckoutView view) {
        if (paymentDetails == null || paymentDetails.cardNumber() == null || paymentDetails.cardNumber().isBlank()) {
            view.showError("Please enter your card details.");
            return;
        }
        String token = getToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }
        String paymentJson;
        try {
            paymentJson = objectMapper.writeValueAsString(paymentDetails);
        } catch (Exception e) {
            view.showError("Could not process payment details.");
            return;
        }
        String code = authCode.isBlank() ? null : authCode.trim();
        Result<OrderHistoryDTO> result = orderService.executeCheckout(token, orderId, code, paymentJson);
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
