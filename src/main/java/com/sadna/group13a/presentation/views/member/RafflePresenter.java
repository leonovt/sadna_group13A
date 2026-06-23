package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleRegistrationDTO;
import com.sadna.group13a.application.DTO.WinningTicketDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.RaffleService;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RafflePresenter {

    private final RaffleService raffleService;

    public RafflePresenter(RaffleService raffleService) {
        this.raffleService = raffleService;
    }

    /**
     * Used by the view's {@code beforeEnter} guard to keep unauthenticated
     * users off the raffles page entirely (they are routed to login instead).
     */
    public boolean hasAccess() {
        return getToken() != null;
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

    public void handleLeaveRaffle(String raffleId, RaffleView view) {
        String token = requireToken(view);
        if (token == null || isBlank(raffleId, view)) {
            return;
        }

        Result<Void> result = raffleService.leaveRaffle(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showInfo("You have left the raffle.");
            handleLoadMyRaffles(view);
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

    public void handleLoadMyRaffles(RaffleView view) {
        String token = requireToken(view);
        if (token == null) return;
        Result<List<RaffleDTO>> result = raffleService.getRafflesForUser(token);
        if (result.isSuccess()) {
            view.showMyRaffles(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCheckResult(String raffleId, RaffleView view) {
        String token = requireToken(view);
        if (token == null || isBlank(raffleId, view)) {
            return;
        }

        Result<WinningTicketDTO> result = raffleService.checkMyResult(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showWinningTicket(result.getOrThrow());
        } else {
            // Not winning (or a draw that hasn't run yet) is a normal outcome of this
            // query, not a failure on the user's part, so it is shown in neutral styling
            // rather than as a red error.
            view.showNeutral(result.getErrorMessage());
        }
    }

    /**
     * Reads the session token defensively: {@code VaadinSession.getCurrent()} can be
     * {@code null} when called outside a Vaadin request thread, so it must be guarded
     * before dereferencing.
     */
    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    private String requireToken(RaffleView view) {
        String token = getToken();
        if (token == null) {
            view.showError("You must be logged in to access raffles.");
            return null;
        }
        return token;
    }

    private boolean isBlank(String raffleId, RaffleView view) {
        if (raffleId == null || raffleId.isBlank()) {
            view.showError("Please enter a raffle ID.");
            return true;
        }
        return false;
    }
}
