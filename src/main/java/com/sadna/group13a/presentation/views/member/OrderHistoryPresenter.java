package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.Services.OrderService;
import org.springframework.stereotype.Component;

@Component
public class OrderHistoryPresenter {

    private final OrderService orderService;

    public OrderHistoryPresenter(OrderService orderService) {
        this.orderService = orderService;
    }
}
