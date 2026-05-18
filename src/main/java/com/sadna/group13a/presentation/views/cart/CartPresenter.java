package com.sadna.group13a.presentation.views.cart;

import com.sadna.group13a.application.Services.OrderService;
import org.springframework.stereotype.Component;

@Component
public class CartPresenter {

    private final OrderService orderService;

    public CartPresenter(OrderService orderService) {
        this.orderService = orderService;
    }
}
