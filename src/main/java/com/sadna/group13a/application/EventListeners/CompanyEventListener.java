package com.sadna.group13a.application.EventListeners;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CompanyEventListener {

    private final ICompanyRepository companyRepository;

    public CompanyEventListener(ICompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @EventListener
    public void onUserBanned(UserBannedEvent event) {
        String bannedUserId = event.targetUserId();
        for (ProductionCompany company : companyRepository.findAll()) {
            if (company.getStaff().containsKey(bannedUserId)) {
                company.forceRemoveStaff(bannedUserId);
                companyRepository.save(company);
            }
        }
    }
}
