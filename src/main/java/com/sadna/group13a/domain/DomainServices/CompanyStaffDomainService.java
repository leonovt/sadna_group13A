package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Encapsulates staff removal business rules for a ProductionCompany:
 * firing or removing a staff member cascades to everyone they appointed
 * (their subtree), and resigning removes the actor together with their subtree.
 */
public class CompanyStaffDomainService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyStaffDomainService.class);

    /**
     * Fires {@code targetId} from the company and cascades the removal to all
     * staff members that were appointed by them (their full subtree).
     * The company aggregate is mutated in-place; callers are responsible for
     * persisting it afterward.
     *
     * @param company  the company aggregate to mutate
     * @param actorId  the user performing the removal
     * @param targetId the staff member being fired
     * @return the full subtree of IDs that were removed (includes {@code targetId})
     */
    public Set<String> cascadeRemove(ProductionCompany company, String actorId, String targetId) {
        Set<String> subtree = company.getStaffSubTree(targetId);
        company.fireStaff(actorId, targetId);
        for (String uid : subtree) {
            if (!uid.equals(targetId) && company.getStaff().containsKey(uid)) {
                company.fireStaff(actorId, uid);
            }
        }
        logger.debug("cascadeRemove: actor '{}' removed '{}' and {} subtree member(s) from company '{}'.",
                actorId, targetId, subtree.size() - 1, company.getId());
        return subtree;
    }

    /**
     * Resigns {@code actorId} from the company and collects the subtree of staff
     * members they had appointed (the calling service is responsible for removing
     * those roles from the user aggregates).
     * The company aggregate is mutated in-place; callers are responsible for
     * persisting it afterward.
     *
     * @param company the company aggregate to mutate
     * @param actorId the staff member who is resigning
     * @return the subtree of IDs that were implicitly orphaned by the resignation
     */
    public Set<String> resignAndGetSubtree(ProductionCompany company, String actorId) {
        Set<String> subtree = company.getStaffSubTree(actorId);
        company.resign(actorId);
        logger.debug("resignAndGetSubtree: user '{}' resigned from company '{}', {} subtree member(s) affected.",
                actorId, company.getId(), subtree.size());
        return subtree;
    }
}
