package com.sadna.group13a.domain.DomainServices;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Domain Service — pure Java, no Spring annotations.
 * Encapsulates staff removal business rules for a ProductionCompany:
 * firing/removing a staff member, or resigning, removes only that single member.
 * Their direct appointees are NOT removed — the aggregate re-parents them to the
 * removed member's appointer so they stay active staff (see issue #367).
 */
public class CompanyStaffDomainService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyStaffDomainService.class);

    /**
     * Removes {@code targetId} from the company. Only that member is removed:
     * the aggregate re-parents the target's direct appointees to the target's
     * appointer (e.g. a fired owner's managers are transferred to the founder),
     * keeping them active staff with their permissions intact (issue #367).
     * The company aggregate is mutated in-place; callers are responsible for
     * persisting it afterward.
     *
     * @param company  the company aggregate to mutate
     * @param actorId  the user performing the removal
     * @param targetId the staff member being removed
     * @return the set of IDs actually removed — exactly {@code targetId}
     */
    public Set<String> removeStaffMember(ProductionCompany company, String actorId, String targetId) {
        company.fireStaff(actorId, targetId);
        logger.debug("removeStaffMember: actor '{}' removed '{}' from company '{}'; appointees re-parented to the removed member's appointer.",
                actorId, targetId, company.getId());
        return Set.of(targetId);
    }

    /**
     * Resigns {@code actorId} from the company. Only the resigning member is
     * removed: the aggregate re-parents their direct appointees to the resigning
     * member's appointer, keeping them active staff (issue #367).
     * The company aggregate is mutated in-place; callers are responsible for
     * persisting it afterward.
     *
     * @param company the company aggregate to mutate
     * @param actorId the staff member who is resigning
     * @return the set of IDs actually removed — exactly {@code actorId}
     */
    public Set<String> resign(ProductionCompany company, String actorId) {
        company.resign(actorId);
        logger.debug("resign: user '{}' resigned from company '{}'; appointees re-parented to the resigning member's appointer.",
                actorId, company.getId());
        return Set.of(actorId);
    }
}
