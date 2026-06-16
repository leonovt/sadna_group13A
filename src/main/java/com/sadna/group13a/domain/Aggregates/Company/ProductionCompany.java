package com.sadna.group13a.domain.Aggregates.Company;

import com.sadna.group13a.domain.policies.discount.NoDiscountPolicy;
import com.sadna.group13a.domain.policies.purchase.AllowAllPolicy;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.infrastructure.converters.DiscountPolicyConverter;
import com.sadna.group13a.infrastructure.converters.PurchasePolicyConverter;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The root entity of the Company aggregate.
 * Represents a production company that hosts events.
 */
@Entity
@Table(name = "companies")
public class ProductionCompany {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyStatus status;

    // userId -> CompanyStaffMember
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "company_id")
    @MapKey(name = "userId")
    private Map<String, CompanyStaffMember> staff;

    // nomineeId -> AppointmentRequest
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "company_id")
    @MapKey(name = "nomineeId")
    private Map<String, AppointmentRequest> pendingAppointments;

    @Convert(converter = PurchasePolicyConverter.class)
    @Column(name = "purchase_policy", columnDefinition = "TEXT")
    private PurchasePolicy purchasePolicy;

    @Convert(converter = DiscountPolicyConverter.class)
    @Column(name = "discount_policy", columnDefinition = "TEXT")
    private DiscountPolicy discountPolicy;

    /** Managed by JPA for optimistic-locking; also incremented manually for in-memory conflict detection. */
    @Version
    @Column(name = "version", nullable = false)
    private volatile long version = 0L;

    /** Required by JPA. Do not use in business code. */
    protected ProductionCompany() {}

    public ProductionCompany(String id, String name, String description, String ownerId) {
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
        this.description = description != null ? description : "";
        this.status = CompanyStatus.ACTIVE;
        this.staff = new ConcurrentHashMap<>();
        this.pendingAppointments = new ConcurrentHashMap<>();

        // Founder has no appointer and full permissions (though permissions are typically checked for MANAGER)
        this.staff.put(ownerId, new CompanyStaffMember(ownerId, CompanyRole.FOUNDER, null, null));

        this.purchasePolicy = new AllowAllPolicy();
        this.discountPolicy = new NoDiscountPolicy();
    }

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
        version++;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be null or blank");
        }
        this.name = name;
        version++;
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
        version++;
    }

    /**
     * Admin override: removes a staff member and re-parents their appointees to the removed member's appointer.
     * Called when a user is banned system-wide.
     * If the removed member is the FOUNDER, the company is auto-closed: an active company
     * must always have at least one owner, and there is no mechanism to promote a new founder.
     */
    public void forceRemoveStaff(String userId) {
        CompanyStaffMember member = staff.get(userId);
        if (member != null) {
            if (member.getRole() == CompanyRole.FOUNDER) {
                this.status = CompanyStatus.INACTIVE;
            }
            removeAndReparent(userId, member.getAppointedByUserId());
            version++;
        }
    }

    /**
     * Admin override: force-closes the company without a founder check.
     * Only called from AdminService after verifying admin authority.
     */
    public void forceClose() {
        this.status = CompanyStatus.INACTIVE;
        version++;
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
        version++;
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

    public boolean hasPermission(String userId, CompanyPermission permission) {
        CompanyStaffMember member = staff.get(userId);
        if (member == null) return false;
        if (CompanyRole.FOUNDER.equals(member.getRole()) || CompanyRole.OWNER.equals(member.getRole())) return true;
        return member.getPermissions().contains(permission);
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
        version++;
    }

    /**
     * The nominated user explicitly rejects the appointment.
     * @param nomineeId the user rejecting the nomination
     */
    public void rejectNomination(String nomineeId) {
        AppointmentRequest req = pendingAppointments.remove(nomineeId);
        if (req == null) {
            throw new DomainException("No pending appointment found for this user");
        }
        version++;
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
        version++;
    }

    /**
     * Fires a staff member. Enforces that only the direct appointer can fire.
     * Direct appointees of the fired member are re-parented to the firing user.
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

        removeAndReparent(targetUserId, target.getAppointedByUserId());
        version++;
    }

    /**
     * Voluntary resignation of a staff member.
     * Direct appointees are re-parented to the resigning member's appointer.
     * @param actingUserId the user resigning
     */
    public void resign(String actingUserId) {
        CompanyStaffMember actor = staff.get(actingUserId);
        if (actor == null) throw new DomainException("User is not part of the company");
        if (actor.getRole() == CompanyRole.FOUNDER) throw new DomainException("Founder cannot resign");

        removeAndReparent(actingUserId, actor.getAppointedByUserId());
        version++;
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
        version++;
    }

    /**
     * Removes a staff member and re-parents their direct appointees to newParentId.
     */
    private void removeAndReparent(String userId, String newParentId) {
        staff.remove(userId);
        for (CompanyStaffMember member : staff.values()) {
            if (userId.equals(member.getAppointedByUserId())) {
                member.setAppointedByUserId(newParentId);
            }
        }
    }

    // ── Policy Management ─────────────────────────────────────────

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    /** Replaces the company-level purchase policy root. Pass AllowAllPolicy to remove restrictions. */
    public void setPurchasePolicy(PurchasePolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Purchase policy cannot be null");
        this.purchasePolicy = policy;
        version++;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    /** Replaces the company-level discount policy root. Pass NoDiscountPolicy to remove discounts. */
    public void setDiscountPolicy(DiscountPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("Discount policy cannot be null");
        this.discountPolicy = policy;
        version++;
    }
}
