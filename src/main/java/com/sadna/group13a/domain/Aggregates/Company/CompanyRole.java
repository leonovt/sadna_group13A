package com.sadna.group13a.domain.Aggregates.Company;

/**
 * Roles a user can hold within a ProductionCompany.
 */
public enum CompanyRole {
    /** The original creator of the company. Only they can close it. */
    FOUNDER,

    /** An owner who can manage the company and hire managers, but cannot close it. */
    OWNER,
    
    /** A manager who can manage events and other managers. */
    MANAGER
}