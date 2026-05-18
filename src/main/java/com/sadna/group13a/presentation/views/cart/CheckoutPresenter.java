package com.sadna.group13a.presentation.views.cart;

import com.sadna.group13a.application.Services.OrderService;
import org.springframework.stereotype.Component;

@Component
public class CheckoutPresenter {

    private final OrderService orderService;

    public CheckoutPresenter(OrderService orderService) {
        this.orderService = orderService;
    }
}
