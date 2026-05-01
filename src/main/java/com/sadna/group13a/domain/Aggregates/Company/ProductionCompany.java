package com.sadna.group13a.domain.Aggregates.Company;

import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.domain.shared.DomainException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The root entity of the Company aggregate.
 * Represents a production company that hosts events.
 */
public class ProductionCompany {

    private final String id;
    private String name;
    private CompanyStatus status;
    
    // userId -> CompanyStaffMember
    private final Map<String, CompanyStaffMember> staff;
    
    // nomineeId -> AppointmentRequest
    private final Map<String, AppointmentRequest> pendingAppointments;
    
    private final List<PurchasePolicy> purchasePolicies;
    private final List<DiscountPolicy> discountPolicies;

    public ProductionCompany(String id, String name, String ownerId) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Company id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be null or blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id cannot be null or blank");
        }

        this.id = id;
        this.name = name;
        this.status = CompanyStatus.ACTIVE;
        this.staff = new ConcurrentHashMap<>();
        this.pendingAppointments = new ConcurrentHashMap<>();
        
        // Founder has no appointer and full permissions (though permissions are typically checked for MANAGER)
        this.staff.put(ownerId, new CompanyStaffMember(ownerId, CompanyRole.FOUNDER, null, null));
        
        this.purchasePolicies = Collections.synchronizedList(new ArrayList<>());
        this.discountPolicies = Collections.synchronizedList(new ArrayList<>());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be null or blank");
        }
        this.name = name;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    /**
     * Suspends/Closes the company.
     * @param actingUserId the user performing the action (must be FOUNDER)
     */
    public void suspendCompany(String actingUserId) {
        if (!isFounder(actingUserId)) {
            throw new DomainException("Only the founder can suspend or close the company");
        }
        this.status = CompanyStatus.INACTIVE;
    }

    /**
     * Reopens a previously suspended/closed company.
     * @param actingUserId the user performing the action (must be FOUNDER)
     */
    public void reopenCompany(String actingUserId) {
        if (!isFounder(actingUserId)) {
            throw new DomainException("Only the founder can reopen the company");
        }
        this.status = CompanyStatus.ACTIVE;
    }

    /**
     * Internal/Persistence use only. 
     * For business logic, use getRoleTree(actingUserId).
     */
    public Map<String, CompanyStaffMember> getStaff() {
        return Collections.unmodifiableMap(staff);
    }

    /**
     * Req 15: Role Tree Transparency & Auditing
     * Any Owner (or Founder) can request the full company structure.
     * @param actingUserId user requesting the tree
     * @return map of all staff members (which includes appointedBy and permissions)
     */
    public Map<String, CompanyStaffMember> getRoleTree(String actingUserId) {
        if (!isOwner(actingUserId)) {
            throw new DomainException("Only founders and owners can view the role tree");
        }
        return Collections.unmodifiableMap(staff);
    }
    
    /**
     * Returns the target user and all their transitive appointees (sub-tree).
     * Useful for determining the scope of Sales Reports.
     * @param rootUserId the user whose sub-tree is being queried
     * @return set of user IDs in the sub-tree
     */
    public Set<String> getStaffSubTree(String rootUserId) {
        if (!staff.containsKey(rootUserId)) {
            throw new DomainException("User is not part of the company");
        }
        Set<String> subTree = new HashSet<>();
        collectSubTree(rootUserId, subTree);
        return subTree;
    }

    private void collectSubTree(String currentUserId, Set<String> result) {
        result.add(currentUserId);
        for (CompanyStaffMember member : staff.values()) {
            if (currentUserId.equals(member.getAppointedByUserId())) {
                collectSubTree(member.getUserId(), result);
            }
        }
    }
    
    public Map<String, AppointmentRequest> getPendingAppointments() {
        return Collections.unmodifiableMap(pendingAppointments);
    }

    public boolean isFounder(String userId) {
        CompanyStaffMember member = staff.get(userId);
        return member != null && CompanyRole.FOUNDER.equals(member.getRole());
    }

    public boolean isOwner(String userId) {
        CompanyStaffMember member = staff.get(userId);
        if (member == null) return false;
        return CompanyRole.OWNER.equals(member.getRole()) || CompanyRole.FOUNDER.equals(member.getRole());
    }

    public boolean isManager(String userId) {
        return staff.containsKey(userId);
    }

    /**
     * Nominates a user for a staff role.
     * @param actingUserId the user performing the action (must be OWNER/FOUNDER)
     * @param nomineeId the user to nominate
     * @param role the role to assign
     * @param permissions the permissions (only applicable for MANAGER)
     */
    public void nominateStaff(String actingUserId, String nomineeId, CompanyRole role, Set<CompanyPermission> permissions) {
        CompanyStaffMember actor = staff.get(actingUserId);
        if (actor == null) {
            throw new DomainException("Acting user is not part of the company");
        }

        if (staff.containsKey(nomineeId)) {
            throw new DomainException("User is already part of the company staff");
        }
        
        if (pendingAppointments.containsKey(nomineeId)) {
            throw new DomainException("User already has a pending appointment");
        }

        if (role == CompanyRole.FOUNDER) {
            throw new DomainException("Cannot appoint a new founder");
        }

        if (role == CompanyRole.OWNER && !isOwner(actingUserId)) {
            throw new DomainException("Only founders and owners can appoint owners");
        }

        if (role == CompanyRole.MANAGER && !isOwner(actingUserId)) {
            throw new DomainException("Only founders and owners can appoint managers");
        }

        if (nomineeId == null || nomineeId.isBlank()) {
            throw new IllegalArgumentException("Nominee id cannot be null or blank");
        }
        
        pendingAppointments.put(nomineeId, new AppointmentRequest(nomineeId, actingUserId, role, permissions));
    }

    /**
     * The nominated user explicitly accepts the appointment.
     * @param nomineeId the user accepting the nomination
     */
    public void acceptNomination(String nomineeId) {
        AppointmentRequest req = pendingAppointments.remove(nomineeId);
        if (req == null) {
            throw new DomainException("No pending appointment found");
        }
        staff.put(nomineeId, new CompanyStaffMember(nomineeId, req.getProposedRole(), req.getAppointerId(), req.getProposedPermissions()));
    }

    /**
     * Fires a staff member. Enforces that only the direct appointer can fire.
     * Cascades down to remove the entire sub-tree appointed by the fired staff member.
     * @param actingUserId the user performing the firing
     * @param targetUserId the staff member to fire
     */
    public void fireStaff(String actingUserId, String targetUserId) {
        CompanyStaffMember target = staff.get(targetUserId);
        
        if (target == null) {
            throw new DomainException("Target user is not part of the company");
        }
        if (targetUserId.equals(actingUserId)) {
            throw new DomainException("Cannot fire yourself, use resign instead");
        }
        if (target.getRole() == CompanyRole.FOUNDER) {
            throw new DomainException("The founder cannot be fired");
        }
        
        if (!actingUserId.equals(target.getAppointedByUserId())) {
            throw new DomainException("You can only fire staff that you directly appointed");
        }

        removeSubtree(targetUserId);
    }

    /**
     * Voluntary resignation of a staff member.
     * Cascades down to remove their entire sub-tree.
     * @param actingUserId the user resigning
     */
    public void resign(String actingUserId) {
        CompanyStaffMember actor = staff.get(actingUserId);
        if (actor == null) throw new DomainException("User is not part of the company");
        if (actor.getRole() == CompanyRole.FOUNDER) throw new DomainException("Founder cannot resign");
        
        removeSubtree(actingUserId);
    }
    
    /**
     * Updates permissions for a MANAGER. Only the direct appointer can do this.
     * @param actingUserId the user making the change
     * @param targetManagerId the manager whose permissions are changing
     * @param newPermissions the new set of permissions
     */
    public void updatePermissions(String actingUserId, String targetManagerId, Set<CompanyPermission> newPermissions) {
        CompanyStaffMember target = staff.get(targetManagerId);
        if (target == null) throw new DomainException("Target user is not part of the company");
        if (target.getRole() != CompanyRole.MANAGER) throw new DomainException("Permissions can only be updated for managers");
        
        if (!actingUserId.equals(target.getAppointedByUserId())) {
            throw new DomainException("Only the direct appointer can modify permissions");
        }
        
        target.setPermissions(newPermissions);
    }

    /**
     * Recursive helper to remove a staff member and all their transitive appointees.
     */
    private void removeSubtree(String rootUserId) {
        staff.remove(rootUserId);
        
        List<String> children = new ArrayList<>();
        for (CompanyStaffMember member : staff.values()) {
            if (rootUserId.equals(member.getAppointedByUserId())) {
                children.add(member.getUserId());
            }
        }
        for (String childId : children) {
            removeSubtree(childId);
        }
    }

    public List<PurchasePolicy> getPurchasePolicies() {
        return Collections.unmodifiableList(purchasePolicies);
    }

    public void addPurchasePolicy(PurchasePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Policy cannot be null");
        purchasePolicies.add(policy);
    }

    public List<DiscountPolicy> getDiscountPolicies() {
        return Collections.unmodifiableList(discountPolicies);
    }

    public void addDiscountPolicy(DiscountPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Policy cannot be null");
        discountPolicies.add(policy);
    }
}