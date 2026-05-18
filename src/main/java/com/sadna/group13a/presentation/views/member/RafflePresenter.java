package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.Services.RaffleService;
import org.springframework.stereotype.Component;

@Component
public class RafflePresenter {

    private final RaffleService raffleService;

    public RafflePresenter(RaffleService raffleService) {
        this.raffleService = raffleService;
    }
}
