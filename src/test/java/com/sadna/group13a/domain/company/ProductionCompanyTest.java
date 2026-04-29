package com.sadna.group13a.domain.company;

import com.sadna.group13a.domain.policy.DiscountPolicy;
import com.sadna.group13a.domain.policy.PurchasePolicy;
import com.sadna.group13a.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductionCompany Aggregate Tests")
class ProductionCompanyTest {

    private ProductionCompany company;

    @BeforeEach
    void setUp() {
        company = new ProductionCompany("c1", "Live Nation", "founder");
    }

    @Test
    @DisplayName("Given valid params — When creating company — Then fields are set and founder assigned")
    void GivenValidParams_WhenCreating_ThenInitialized() {
        assertEquals("c1", company.getId());
        assertEquals(CompanyStatus.ACTIVE, company.getStatus());
        assertTrue(company.isFounder("founder"));
        assertTrue(company.isOwner("founder"));
        assertEquals(1, company.getStaff().size());
    }

    @Test
    @DisplayName("Given founder — When nominating owner — Then appointment is pending")
    void GivenFounder_WhenNominateOwner_ThenPending() {
        company.nominateStaff("founder", "user-owner-1", CompanyRole.OWNER, null);
        
        assertTrue(company.getPendingAppointments().containsKey("user-owner-1"));
        assertFalse(company.isOwner("user-owner-1"));
        
        company.acceptNomination("user-owner-1");
        
        assertTrue(company.isOwner("user-owner-1"));
        assertEquals("founder", company.getStaff().get("user-owner-1").getAppointedByUserId());
    }

    @Test
    @DisplayName("Given owner — When nominating another owner — Then nominated successfully")
    void GivenOwner_WhenNominateOwner_ThenSucceeds() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        
        company.nominateStaff("owner-1", "owner-2", CompanyRole.OWNER, null);
        company.acceptNomination("owner-2");

        assertTrue(company.isOwner("owner-2"));
        assertEquals("owner-1", company.getStaff().get("owner-2").getAppointedByUserId());
    }

    @Test
    @DisplayName("Given founder/owner — When nominating manager — Then manager is nominated")
    void GivenOwner_WhenNominateManager_ThenNominated() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        
        company.nominateStaff("owner-1", "manager-1", CompanyRole.MANAGER, Set.of(CompanyPermission.MANAGE_EVENTS));
        company.acceptNomination("manager-1");
        
        assertTrue(company.isManager("manager-1"));
        assertEquals(CompanyRole.MANAGER, company.getStaff().get("manager-1").getRole());
        assertEquals("owner-1", company.getStaff().get("manager-1").getAppointedByUserId());
        assertTrue(company.getStaff().get("manager-1").getPermissions().contains(CompanyPermission.MANAGE_EVENTS));
    }

    @Test
    @DisplayName("Given manager — When firing sub-manager — Then cascades properly")
    void GivenOwner_WhenFired_ThenCascades() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        
        company.nominateStaff("owner-1", "manager-1", CompanyRole.MANAGER, null);
        company.acceptNomination("manager-1");
        
        assertTrue(company.isOwner("owner-1"));
        assertTrue(company.isManager("manager-1"));
        
        // Founder fires owner-1
        company.fireStaff("founder", "owner-1");
        
        assertFalse(company.isOwner("owner-1"));
        // manager-1 should also be fired (cascading removal)
        assertFalse(company.isManager("manager-1"));
    }

    @Test
    @DisplayName("Given owner — When firing peer or superior — Then throws")
    void GivenOwner_WhenFirePeer_ThenThrows() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        company.nominateStaff("founder", "owner-2", CompanyRole.OWNER, null);
        company.acceptNomination("owner-2");
        
        assertThrows(DomainException.class, () -> company.fireStaff("owner-1", "founder"));
        assertThrows(DomainException.class, () -> company.fireStaff("owner-1", "owner-2"));
    }

    @Test
    @DisplayName("Given manager — When updating permissions — Then succeeds only for direct appointer")
    void GivenManager_WhenUpdatingPermissions_ThenEnforced() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        
        company.nominateStaff("owner-1", "manager-1", CompanyRole.MANAGER, Set.of(CompanyPermission.MANAGE_EVENTS));
        company.acceptNomination("manager-1");
        
        assertThrows(DomainException.class, 
            () -> company.updatePermissions("founder", "manager-1", Set.of(CompanyPermission.VIEW_REPORTS)));
            
        company.updatePermissions("owner-1", "manager-1", Set.of(CompanyPermission.MANAGE_POLICIES));
        assertTrue(company.getStaff().get("manager-1").getPermissions().contains(CompanyPermission.MANAGE_POLICIES));
        assertFalse(company.getStaff().get("manager-1").getPermissions().contains(CompanyPermission.MANAGE_EVENTS));
    }

    @Test
    @DisplayName("Given founder — When resigning — Then throws")
    void GivenFounder_WhenResign_ThenThrows() {
        assertThrows(DomainException.class, () -> company.resign("founder"));
    }

    @Test
    @DisplayName("Given founder — When suspending and reopening — Then state changes correctly")
    void GivenFounder_WhenSuspendAndReopen_ThenStateChanges() {
        company.suspendCompany("founder");
        assertEquals(CompanyStatus.INACTIVE, company.getStatus());

        company.reopenCompany("founder");
        assertEquals(CompanyStatus.ACTIVE, company.getStatus());
    }

    @Test
    @DisplayName("Given non-founder — When suspending or reopening — Then throws")
    void GivenNonFounder_WhenSuspendOrReopen_ThenThrows() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");

        assertThrows(DomainException.class, () -> company.suspendCompany("owner-1"));
        assertThrows(DomainException.class, () -> company.reopenCompany("owner-1"));
    }
    @Test
    @DisplayName("Given owner — When requesting role tree — Then tree returned")
    void GivenOwner_WhenRequestRoleTree_ThenReturned() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");

        Map<String, CompanyStaffMember> tree = company.getRoleTree("owner-1");
        assertEquals(2, tree.size());
        assertTrue(tree.containsKey("founder"));
        assertTrue(tree.containsKey("owner-1"));
    }

    @Test
    @DisplayName("Given manager — When requesting role tree — Then throws DomainException")
    void GivenManager_WhenRequestRoleTree_ThenThrows() {
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        company.nominateStaff("owner-1", "manager-1", CompanyRole.MANAGER, null);
        company.acceptNomination("manager-1");

        assertThrows(DomainException.class, () -> company.getRoleTree("manager-1"));
    }

    @Test
    @DisplayName("Given owner — When requesting sub-tree — Then returns only their transitive appointees")
    void GivenOwner_WhenGetSubTree_ThenReturnsCorrectSet() {
        // founder -> owner-1 -> manager-1
        //         -> owner-2
        company.nominateStaff("founder", "owner-1", CompanyRole.OWNER, null);
        company.acceptNomination("owner-1");
        company.nominateStaff("founder", "owner-2", CompanyRole.OWNER, null);
        company.acceptNomination("owner-2");
        company.nominateStaff("owner-1", "manager-1", CompanyRole.MANAGER, null);
        company.acceptNomination("manager-1");

        Set<String> subTree = company.getStaffSubTree("owner-1");
        assertEquals(2, subTree.size());
        assertTrue(subTree.contains("owner-1"));
        assertTrue(subTree.contains("manager-1"));
        assertFalse(subTree.contains("owner-2"));
        assertFalse(subTree.contains("founder"));
    }
}
