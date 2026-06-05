package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Company.CompanyStatus;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Aggregates.Event.Event;
import com.sadna.group13a.domain.Aggregates.Event.Zone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Encapsulates the rules for which events are visible and matchable in a public
 * search: the event must be published, its company must be active, and all
 * caller-supplied filters (text, category, location, date range, price range)
 * must pass.
 */
public class EventSearchDomainService {

    private static final Logger logger = LoggerFactory.getLogger(EventSearchDomainService.class);

    /**
     * Filters a collection of events according to the supplied criteria.
     *
     * @param allEvents      the full event catalogue to search
     * @param companiesById  map of companyId → company, used for active-company check
     * @param query          optional free-text match against title and description
     * @param category       optional exact category match (case-insensitive)
     * @param fromDate       optional lower bound on event date (inclusive)
     * @param toDate         optional upper bound on event date (inclusive)
     * @param minPrice       optional minimum price filter (applied to cheapest zone)
     * @param maxPrice       optional maximum price filter (applied to cheapest zone)
     * @param location       optional partial location match (case-insensitive)
     * @return matching events in encounter order
     */
    public List<Event> search(
            List<Event> allEvents,
            Map<String, ProductionCompany> companiesById,
            String query,
            String category,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Double minPrice,
            Double maxPrice,
            String location) {

        List<Event> results = allEvents.stream()
            .filter(Event::isPublished)
            .filter(e -> {
                ProductionCompany comp = companiesById.get(e.getCompanyId());
                return comp != null && comp.getStatus() == CompanyStatus.ACTIVE;
            })
            .filter(e -> query == null
                    || e.getTitle().toLowerCase().contains(query.toLowerCase())
                    || e.getDescription().toLowerCase().contains(query.toLowerCase()))
            .filter(e -> category == null || e.getCategory().equalsIgnoreCase(category))
            .filter(e -> location == null || (e.getLocation() != null
                    && e.getLocation().toLowerCase().contains(location.toLowerCase())))
            .filter(e -> fromDate == null || !e.getEventDate().isBefore(fromDate))
            .filter(e -> toDate == null || !e.getEventDate().isAfter(toDate))
            .filter(e -> {
                if (minPrice == null && maxPrice == null) return true;
                if (e.getVenueMap() == null) return false;
                double cheapest = e.getVenueMap().getZones().stream()
                        .mapToDouble(Zone::getBasePrice).min().orElse(Double.MAX_VALUE);
                return (minPrice == null || cheapest >= minPrice)
                    && (maxPrice == null || cheapest <= maxPrice);
            })
            .collect(Collectors.toList());

        logger.debug("search: {} result(s) — query='{}' category='{}' location='{}' from='{}' to='{}' minPrice='{}' maxPrice='{}'.",
                results.size(), query, category, location, fromDate, toDate, minPrice, maxPrice);
        return results;
    }
}
