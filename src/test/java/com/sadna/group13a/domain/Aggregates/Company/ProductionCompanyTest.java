package com.sadna.group13a.domain.Aggregates.Company;

import com.sadna.group13a.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductionCompanyTest {

    private static final String FOUNDER_ID  = "founder-1";
    private static final String COMPANY_ID  = "company-1";

    private ProductionCompany company;

    @BeforeEach
    void setUp() {
        company = new ProductionCompany(COMPANY_ID, "AcmeCorp", "Live Events", FOUNDER_ID);
    }

    // ── Construction ──────────────────────────────────────────────

    @Test
    void givenValidParams_whenCreatingCompany_thenFounderIsInStaffAndStatusIsActive() {
        assertEquals(COMPANY_ID, company.getId());
        assertEquals("AcmeCorp", company.getName());
        assertEquals(CompanyStatus.ACTIVE, company.getStatus());
        assertTrue(company.isFounder(FOUNDER_ID));
        assertTrue(company.isOwner(FOUNDER_ID));
    }

    @Test
    void givenNullCompanyId_whenCreatingCompany_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany(null, "name", "desc", FOUNDER_ID));
    }

    @Test
    void givenBlankName_whenCreatingCompany_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany(UUID.randomUUID().toString(), "  ", "desc", FOUNDER_ID));
    }

    @Test
    void givenBlankOwnerId_whenCreatingCompany_thenThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductionCompany(UUID.randomUUID().toString(), "name", "desc", ""));
    }

    // ══════════════════════════════════════════════════════════════
    // Nominate & Accept: Manager
    // ══════════════════════════════════════════════════════════════

    @Nested
    class NominateManagerTests {

        @Test
        void givenFounder_whenNominatingManager_thenManagerIsInStaff() {
            String managerId = "manager-1";

            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertTrue(company.isManager(managerId));
            assertFalse(company.isOwner(managerId));
        }

        @Test
        void givenManager_whenCheckingPermission_thenDefaultPermissionsAreEmpty() {
            String managerId = "manager-1";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertFalse(company.hasPermission(managerId, CompanyPermission.VIEW_REPORTS));
        }

        @Test
        void givenManagerWithPermissions_whenCheckingPermission_thenGrantedPermissionsArePresent() {
            String managerId = "manager-2";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER,
                    Set.of(CompanyPermission.VIEW_REPORTS, CompanyPermission.MANAGE_EVENTS));
            company.acceptNomination(managerId);

            assertTrue(company.hasPermission(managerId, CompanyPermission.VIEW_REPORTS));
            assertTrue(company.hasPermission(managerId, CompanyPermission.MANAGE_EVENTS));
            assertFalse(company.hasPermission(managerId, CompanyPermission.MANAGE_POLICIES));
        }

        @Test
        void givenNominatedManager_whenAcceptNominationTwice_thenSecondThrowsDomainException() {
            String managerId = "manager-3";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class, () -> company.acceptNomination(managerId));
        }

        @Test
        void givenExistingStaff_whenNominatedAgain_thenThrowsDomainException() {
            String managerId = "manager-4";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class,
                    () -> company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null));
        }

        @Test
        void givenNonStaffActing_whenNominatingManager_thenThrowsDomainException() {
            assertThrows(DomainException.class,
                    () -> company.nominateStaff("outsider", "manager-5", CompanyRole.MANAGER, null));
        }

        @Test
        void givenFounder_whenNominatingFOUNDER_thenThrowsDomainException() {
            assertThrows(DomainException.class,
                    () -> company.nominateStaff(FOUNDER_ID, "someone", CompanyRole.FOUNDER, null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Nominate & Accept: Owner
    // ══════════════════════════════════════════════════════════════

    @Nested
    class NominateOwnerTests {

        @Test
        void givenFounder_whenNominatingOwner_thenOwnerIsInStaffWithOwnerRole() {
            String ownerId = "owner-2";
            company.nominateStaff(FOUNDER_ID, ownerId, CompanyRole.OWNER, null);
            company.acceptNomination(ownerId);

            assertTrue(company.isOwner(ownerId));
        }

        @Test
        void givenManager_whenNominatingOwner_thenThrowsDomainException() {
            String managerId = "mgr";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class,
                    () -> company.nominateStaff(managerId, "new-owner", CompanyRole.OWNER, null));
        }

        @Test
        void givenOwner_whenCheckingIsOwner_thenReturnsTrue() {
            String ownerId = "owner-3";
            company.nominateStaff(FOUNDER_ID, ownerId, CompanyRole.OWNER, null);
            company.acceptNomination(ownerId);

            assertTrue(company.isOwner(ownerId));
            assertTrue(company.hasPermission(ownerId, CompanyPermission.VIEW_REPORTS));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // fireStaff
    // ══════════════════════════════════════════════════════════════

    @Nested
    class FireStaffTests {

        @Test
        void givenFounderFiredManager_whenChecking_thenManagerNoLongerInStaff() {
            String managerId = "mgr-fire";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            company.fireStaff(FOUNDER_ID, managerId);

            assertFalse(company.getStaff().containsKey(managerId));
        }

        @Test
        void givenManagerWithSubManagers_whenFired_thenSubManagersReparentedToGrandparent() {
            String mgr1 = "mgr-1";
            String mgr2 = "mgr-2";
            company.nominateStaff(FOUNDER_ID, mgr1, CompanyRole.OWNER, null);
            company.acceptNomination(mgr1);
            company.nominateStaff(mgr1, mgr2, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr2);

            company.fireStaff(FOUNDER_ID, mgr1);

            assertFalse(company.getStaff().containsKey(mgr1));
            assertTrue(company.getStaff().containsKey(mgr2));
            assertEquals(FOUNDER_ID, company.getStaff().get(mgr2).getAppointedByUserId());
        }

        @Test
        void givenNonAppointer_whenFiringManager_thenThrowsDomainException() {
            String mgr1 = "mgr-a";
            String mgr2 = "mgr-b";
            company.nominateStaff(FOUNDER_ID, mgr1, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr1);
            company.nominateStaff(FOUNDER_ID, mgr2, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr2);

            // mgr2 did not appoint mgr1, so cannot fire mgr1
            assertThrows(DomainException.class, () -> company.fireStaff(mgr2, mgr1));
        }

        @Test
        void givenStaff_whenFiresThemselves_thenThrowsDomainException() {
            String managerId = "self-fire";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class, () -> company.fireStaff(managerId, managerId));
        }

        @Test
        void givenActor_whenFiringFounder_thenThrowsDomainException() {
            String ownerId = "owner-x";
            company.nominateStaff(FOUNDER_ID, ownerId, CompanyRole.OWNER, null);
            company.acceptNomination(ownerId);

            assertThrows(DomainException.class, () -> company.fireStaff(ownerId, FOUNDER_ID));
        }

        @Test
        void givenNonExistentTarget_whenFiring_thenThrowsDomainException() {
            assertThrows(DomainException.class, () -> company.fireStaff(FOUNDER_ID, "ghost"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // resign
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ResignTests {

        @Test
        void givenManager_whenResigns_thenRemovedFromStaff() {
            String managerId = "mgr-resign";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            company.resign(managerId);

            assertFalse(company.getStaff().containsKey(managerId));
        }

        @Test
        void givenOwnerWithSubManagers_whenResigns_thenSubManagersReparentedToGrandparent() {
            String owner = "owner-resign";
            String mgr = "mgr-under-owner";
            company.nominateStaff(FOUNDER_ID, owner, CompanyRole.OWNER, null);
            company.acceptNomination(owner);
            company.nominateStaff(owner, mgr, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr);

            company.resign(owner);

            assertFalse(company.getStaff().containsKey(owner));
            assertTrue(company.getStaff().containsKey(mgr));
            assertEquals(FOUNDER_ID, company.getStaff().get(mgr).getAppointedByUserId());
        }

        @Test
        void givenFounder_whenResigns_thenThrowsDomainException() {
            assertThrows(DomainException.class, () -> company.resign(FOUNDER_ID));
        }

        @Test
        void givenOutsider_whenResigns_thenThrowsDomainException() {
            assertThrows(DomainException.class, () -> company.resign("nonmember"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // updatePermissions
    // ══════════════════════════════════════════════════════════════

    @Nested
    class UpdatePermissionsTests {

        @Test
        void givenFounder_whenUpdatingManagerPermissions_thenPermissionsAreReplaced() {
            String managerId = "mgr-perm";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            company.updatePermissions(FOUNDER_ID, managerId, Set.of(CompanyPermission.MANAGE_EVENTS));

            assertTrue(company.hasPermission(managerId, CompanyPermission.MANAGE_EVENTS));
            assertFalse(company.hasPermission(managerId, CompanyPermission.VIEW_REPORTS));
        }

        @Test
        void givenNonAppointer_whenUpdatingPermissions_thenThrowsDomainException() {
            String mgr1 = "mgr-p1";
            String mgr2 = "mgr-p2";
            company.nominateStaff(FOUNDER_ID, mgr1, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr1);
            company.nominateStaff(FOUNDER_ID, mgr2, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr2);

            assertThrows(DomainException.class,
                    () -> company.updatePermissions(mgr2, mgr1, Set.of(CompanyPermission.VIEW_REPORTS)));
        }

        @Test
        void givenOwnerTarget_whenUpdatingPermissions_thenThrowsDomainException() {
            String ownerId = "owner-perms";
            company.nominateStaff(FOUNDER_ID, ownerId, CompanyRole.OWNER, null);
            company.acceptNomination(ownerId);

            assertThrows(DomainException.class,
                    () -> company.updatePermissions(FOUNDER_ID, ownerId, Set.of(CompanyPermission.VIEW_REPORTS)));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // suspend / reopen / forceClose / forceRemoveStaff
    // ══════════════════════════════════════════════════════════════

    @Nested
    class CompanyStatusTests {

        @Test
        void givenActiveCompany_whenFounderSuspends_thenStatusIsInactive() {
            company.suspendCompany(FOUNDER_ID);

            assertEquals(CompanyStatus.INACTIVE, company.getStatus());
        }

        @Test
        void givenNonFounder_whenSuspending_thenThrowsDomainException() {
            String managerId = "mgr-suspend";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class, () -> company.suspendCompany(managerId));
        }

        @Test
        void givenSuspendedCompany_whenFounderReopens_thenStatusIsActive() {
            company.suspendCompany(FOUNDER_ID);
            company.reopenCompany(FOUNDER_ID);

            assertEquals(CompanyStatus.ACTIVE, company.getStatus());
        }

        @Test
        void givenNonFounder_whenReopening_thenThrowsDomainException() {
            company.suspendCompany(FOUNDER_ID);
            String ownerId = "owner-reopen";
            company.nominateStaff(FOUNDER_ID, ownerId, CompanyRole.OWNER, null);
            company.acceptNomination(ownerId);

            assertThrows(DomainException.class, () -> company.reopenCompany(ownerId));
        }

        @Test
        void givenActiveCompany_whenForceClosed_thenStatusIsInactive() {
            company.forceClose();

            assertEquals(CompanyStatus.INACTIVE, company.getStatus());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // forceRemoveStaff (admin cascade on user ban)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ForceRemoveStaffTests {

        @Test
        void givenManagerInStaff_whenForceRemoved_thenManagerIsRemovedFromStaff() {
            String managerId = "mgr-ban";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            company.forceRemoveStaff(managerId);

            assertFalse(company.getStaff().containsKey(managerId));
        }

        @Test
        void givenNonStaffUser_whenForceRemoved_thenNoExceptionThrown() {
            assertDoesNotThrow(() -> company.forceRemoveStaff("nonexistent-user"));
        }

        @Test
        void givenManagerWithSubManagers_whenForceRemoved_thenSubManagersReparentedToGrandparent() {
            String mgr1 = "mgr-ban-1";
            String mgr2 = "mgr-ban-2";
            company.nominateStaff(FOUNDER_ID, mgr1, CompanyRole.OWNER, null);
            company.acceptNomination(mgr1);
            company.nominateStaff(mgr1, mgr2, CompanyRole.MANAGER, null);
            company.acceptNomination(mgr2);

            company.forceRemoveStaff(mgr1);

            assertFalse(company.getStaff().containsKey(mgr1));
            assertTrue(company.getStaff().containsKey(mgr2));
            assertEquals(FOUNDER_ID, company.getStaff().get(mgr2).getAppointedByUserId());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // getRoleTree
    // ══════════════════════════════════════════════════════════════

    @Nested
    class RoleTreeTests {

        @Test
        void givenOwner_whenGettingRoleTree_thenReturnsAllStaff() {
            String managerId = "mgr-tree";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            var tree = company.getRoleTree(FOUNDER_ID);

            assertTrue(tree.containsKey(FOUNDER_ID));
            assertTrue(tree.containsKey(managerId));
        }

        @Test
        void givenManager_whenGettingRoleTree_thenThrowsDomainException() {
            String managerId = "mgr-no-tree";
            company.nominateStaff(FOUNDER_ID, managerId, CompanyRole.MANAGER, null);
            company.acceptNomination(managerId);

            assertThrows(DomainException.class, () -> company.getRoleTree(managerId));
        }
    }
}
