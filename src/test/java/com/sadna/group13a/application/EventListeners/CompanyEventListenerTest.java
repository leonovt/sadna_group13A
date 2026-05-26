package com.sadna.group13a.application.EventListeners;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Events.UserBannedEvent;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit tests for CompanyEventListener — verifies that when a user is banned
 * the listener removes them from every company they belong to.
 */
@DisplayName("CompanyEventListener")
class CompanyEventListenerTest {

    private ICompanyRepository companyRepository;
    private CompanyEventListener listener;

    @BeforeEach
    void setUp() {
        companyRepository = mock(ICompanyRepository.class);
        listener = new CompanyEventListener(companyRepository);
    }

    @Test
    @DisplayName("Given banned user is staff in a company — When UserBannedEvent fires — Then user is removed and company saved")
    void givenBannedUserIsStaff_whenEventFires_thenRemovedAndSaved() {
        String bannedUserId = "user-banned";
        UserBannedEvent event = new UserBannedEvent(bannedUserId, "admin-1");

        ProductionCompany company = mock(ProductionCompany.class);
        // getStaff() returns a map that contains the banned user
        when(company.getStaff()).thenReturn(Map.of(bannedUserId, mock(
                com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember.class)));
        when(companyRepository.findAll()).thenReturn(List.of(company));

        listener.onUserBanned(event);

        verify(company).forceRemoveStaff(bannedUserId);
        verify(companyRepository).save(company);
    }

    @Test
    @DisplayName("Given banned user is NOT in any company — When UserBannedEvent fires — Then no company is modified")
    void givenBannedUserNotInAnyCompany_whenEventFires_thenNoChanges() {
        String bannedUserId = "user-not-staff";
        UserBannedEvent event = new UserBannedEvent(bannedUserId, "admin-1");

        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getStaff()).thenReturn(Map.of("other-user", mock(
                com.sadna.group13a.domain.Aggregates.Company.CompanyStaffMember.class)));
        when(companyRepository.findAll()).thenReturn(List.of(company));

        listener.onUserBanned(event);

        verify(company, never()).forceRemoveStaff(any());
        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given no companies exist — When UserBannedEvent fires — Then nothing happens")
    void givenNoCompanies_whenEventFires_thenNothingHappens() {
        when(companyRepository.findAll()).thenReturn(List.of());

        listener.onUserBanned(new UserBannedEvent("user-1", "admin-1"));

        verify(companyRepository, never()).save(any());
    }
}
