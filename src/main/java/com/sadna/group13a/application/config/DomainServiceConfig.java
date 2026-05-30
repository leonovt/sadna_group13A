package com.sadna.group13a.application.config;

import com.sadna.group13a.domain.DomainServices.CheckoutDomainService;
import com.sadna.group13a.domain.DomainServices.CompanyStaffDomainService;
import com.sadna.group13a.domain.DomainServices.EventSearchDomainService;
import com.sadna.group13a.domain.DomainServices.TicketingAccessDomainService;
import com.sadna.group13a.domain.DomainServices.VenueMapFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public CheckoutDomainService checkoutDomainService() {
        return new CheckoutDomainService();
    }

    @Bean
    public TicketingAccessDomainService ticketingAccessDomainService() {
        return new TicketingAccessDomainService();
    }

    @Bean
    public EventSearchDomainService eventSearchDomainService() {
        return new EventSearchDomainService();
    }

    @Bean
    public VenueMapFactory venueMapFactory() {
        return new VenueMapFactory();
    }

    @Bean
    public CompanyStaffDomainService companyStaffDomainService() {
        return new CompanyStaffDomainService();
    }
}
