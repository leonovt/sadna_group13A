package com.sadna.group13a.domain.company;

/**
 * Operational status of a ProductionCompany.
 */
public enum CompanyStatus {
    /** Company is operating normally, visible in search, tickets can be bought. */
    ACTIVE,
    
    /** Company is closed/suspended. Events hidden, purchases blocked. */
    INACTIVE
}
