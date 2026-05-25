package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.RaffleService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

@Component
public class RafflePresenter {

    private final RaffleService raffleService;

    public RafflePresenter(RaffleService raffleService) {
        this.raffleService = raffleService;
    }

    public void handleJoinRaffle(String raffleId, RaffleView view) {
        String token = requireToken(view);
        if (token == null || isBlank(raffleId, view)) {
            return;
        }

        Result<Void> result = raffleService.joinRaffle(token, new RaffleRegistrationDTO(raffleId.trim()));
        if (result.isSuccess()) {
            view.showInfo("You have successfully joined the raffle.");
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleViewDetails(String raffleId, RaffleView view) {
        String token = requireToken(view);
        if (token == null || isBlank(raffleId, view)) {
            return;
        }

        Result<RaffleDTO> result = raffleService.getRaffleDetails(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showRaffleDetails(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCheckResult(String raffleId, RaffleView view) {
        String token = requireToken(view);
        if (token == null || isBlank(raffleId, view)) {
            return;
        }

        // A non-winning result (or a draw that hasn't run yet) comes back as a failure
        // carrying an explanatory message, so it is surfaced through showError.
        Result<WinningTicketDTO> result = raffleService.checkMyResult(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showWinningTicket(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    private String requireToken(RaffleView view) {
        Object token = VaadinSession.getCurrent().getAttribute("token");
        if (token == null) {
            view.showError("You must be logged in to access raffles.");
            return null;
        }
        return (String) token;
    }

    private boolean isBlank(String raffleId, RaffleView view) {
        if (raffleId == null || raffleId.isBlank()) {
            view.showError("Please enter a raffle ID.");
            return true;
        }
        return false;
    }
}
