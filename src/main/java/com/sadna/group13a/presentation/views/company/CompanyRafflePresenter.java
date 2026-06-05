package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.RaffleDTO;
import com.sadna.group13a.application.DTO.RaffleResultDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.RaffleService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyRafflePresenter {

    private final RaffleService raffleService;

    public CompanyRafflePresenter(RaffleService raffleService) {
        this.raffleService = raffleService;
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAccess() {
        return getToken() != null;
    }

    public void handleLoadCompanyRaffles(String companyId, CompanyRaffleView view) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<RaffleDTO>> result = raffleService.getRafflesForCompany(token, companyId);
        if (result.isSuccess()) {
            view.showRaffleList(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCreateRaffle(String eventId, String companyId, CompanyRaffleView view) {
        if (eventId.isBlank()) { view.showError("Please enter an Event ID."); return; }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<String> result = raffleService.createRaffle(token, eventId.trim(), companyId);
        if (result.isSuccess()) {
            view.showSuccess("Raffle created. Raffle ID: " + result.getOrThrow());
            handleLoadCompanyRaffles(companyId, view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleCloseRaffle(String raffleId, String companyId, CompanyRaffleView view) {
        if (raffleId.isBlank()) { view.showError("Please enter a Raffle ID."); return; }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<Void> result = raffleService.closeRaffle(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showSuccess("Raffle '" + raffleId.trim() + "' closed.");
            handleLoadCompanyRaffles(companyId, view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleDrawWinners(String raffleId, String companyId, String winnersCountStr, String validMinutesStr, CompanyRaffleView view) {
        if (raffleId.isBlank()) { view.showError("Please enter a Raffle ID."); return; }
        int winnersCount;
        int validMinutes;
        try {
            winnersCount = Integer.parseInt(winnersCountStr.trim());
        } catch (NumberFormatException e) {
            view.showError("Number of winners must be a valid integer.");
            return;
        }
        try {
            validMinutes = Integer.parseInt(validMinutesStr.trim());
        } catch (NumberFormatException e) {
            view.showError("Valid minutes must be a valid integer.");
            return;
        }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<RaffleResultDTO> result = raffleService.drawWinners(token, raffleId.trim(), winnersCount, validMinutes);
        if (result.isSuccess()) {
            RaffleResultDTO dto = result.getOrThrow();
            view.showSuccess("Draw complete — " + dto.expectedWinnersDrawn() + " winner(s) selected. Codes valid for " + validMinutes + " min.");
            handleLoadCompanyRaffles(companyId, view);
        } else {
            view.showError(result.getErrorMessage());
        }
    }

    public void handleViewDetails(String raffleId, CompanyRaffleView view) {
        if (raffleId.isBlank()) { view.showError("Please enter a Raffle ID."); return; }
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<RaffleDTO> result = raffleService.getRaffleDetails(token, raffleId.trim());
        if (result.isSuccess()) {
            view.showRaffleDetails(result.getOrThrow());
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
