package com.sadna.group13a.presentation.views.company;

import com.sadna.group13a.application.DTO.OrderHistoryDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyOrderHistoryPresenter {

    private final CompanyService companyService;

    public CompanyOrderHistoryPresenter(CompanyService companyService) {
        this.companyService = companyService;
    }

    private String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public boolean hasAccess() {
        return getToken() != null;
    }

    public void loadOrders(CompanyOrderHistoryView view, String companyId) {
        String token = getToken();
        if (token == null) { UI.getCurrent().navigate("login"); return; }
        Result<List<OrderHistoryDTO>> result = companyService.viewCompanyOrders(token, companyId);
        if (result.isSuccess()) {
            view.displayOrders(result.getData().orElse(List.of()));
        } else {
            view.showError(result.getErrorMessage());
        }
    }
}
