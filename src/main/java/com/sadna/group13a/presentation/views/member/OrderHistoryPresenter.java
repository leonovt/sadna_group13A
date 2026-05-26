package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderHistoryPresenter {

    private final UserService userService;

    public OrderHistoryPresenter(UserService userService) {
        this.userService = userService;
    }

    /** Loads the signed-in member's completed purchases (UC 2.11). */
    public void loadOrders(OrderHistoryView view) {
        String token = currentToken();
        if (token == null) {
            UI.getCurrent().navigate("login");
            return;
        }

        Result<List<OrderHistoryDTO>> result = userService.viewOrderHistory(token);
        if (result.isSuccess()) {
            view.showOrders(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String currentToken() {
        // VaadinSession.getCurrent() can be null when called outside a Vaadin
        // request thread, so it must be guarded before dereferencing.
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }
        Object token = session.getAttribute("token");
        return token == null ? null : (String) token;
    }
}
