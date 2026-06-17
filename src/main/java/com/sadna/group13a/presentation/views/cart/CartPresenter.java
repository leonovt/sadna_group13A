package com.sadna.group13a.presentation.views.cart;

import com.sadna.group13a.application.DTO.OrderDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.OrderService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class CartPresenter {

    private final OrderService orderService;

    public CartPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAccess() {
        return getToken() != null;
    }

    public void loadCart(CartView view) {
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
                view.displayEmpty();
            }
        } else {
            view.displayEmpty();
        }
    }

    public void handleRemoveItem(String eventId, String zoneId, String seatId, CartView view) {
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

    public void handleCancelCart(CartView view) {
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
