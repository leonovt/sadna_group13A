package com.sadna.group13a.presentation.views.home;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.AdminService;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.PendingNotificationService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.infrastructure.notification.NotificationBroadcaster;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class HomePresenter {

    private final EventService eventService;
    private final UserService userService;
    private final CompanyService companyService;
    private final AdminService adminService;
    private final IAuth authGateway;
    private final NotificationBroadcaster broadcaster;
    private final PendingNotificationService pendingNotificationService;

    public HomePresenter(EventService eventService, UserService userService,
                         CompanyService companyService, AdminService adminService,
                         IAuth authGateway, NotificationBroadcaster broadcaster,
                         PendingNotificationService pendingNotificationService) {
        this.eventService = eventService;
        this.userService = userService;
        this.companyService = companyService;
        this.adminService = adminService;
        this.authGateway = authGateway;
        this.broadcaster = broadcaster;
        this.pendingNotificationService = pendingNotificationService;
    }

    public boolean isAdmin(String token) {
        if (token == null) return false;
        return adminService.getSystemAnalytics(token).isSuccess();
    }

    public Result<List<EventDTO>> loadEvents(String query, String category, String location, String artist) {
        String cat = (category != null && category.isBlank()) ? null : category;
        String loc = (location != null && location.isBlank()) ? null : location;
        String art = (artist != null && artist.isBlank()) ? null : artist;
        return eventService.searchEvents(query, cat, null, null, null, null, loc, art);
    }

    public Result<UserDTO> loadUserProfile(String token) {
        return userService.getUserProfile(token);
    }

    public List<CompanyDTO> getMyCompanies(String token) {
        Result<List<CompanyDTO>> result = companyService.getMyCompanies(token);
        return result.isSuccess() ? result.getOrThrow() : Collections.emptyList();
    }

    public boolean isTokenValid(String token) {
        return authGateway.validateToken(token);
    }

    public String getUserId(String token) {
        return authGateway.extractUserId(token);
    }

    public void registerForNotifications(String userId, UI ui) {
        // Register the live UI session (in-memory) and then deliver any notifications that
        // arrived while the user was offline. The read-and-clear runs inside the application
        // layer's transaction (PendingNotificationService) — the Vaadin UI thread has no
        // active transaction, so doing the JPA delete here directly would throw
        // TransactionRequiredException.
        broadcaster.register(userId, ui);
        List<String> pending = pendingNotificationService.drainPending(userId);
        broadcaster.deliverPending(ui, pending);
    }

    public void unregisterFromNotifications(String userId) {
        broadcaster.unregister(userId);
    }

    public void handleLogout(String token) {
        Result<String> result = userService.logout(token);
        if (result.isSuccess()) {
            VaadinSession.getCurrent().setAttribute("token", result.getOrThrow());
        } else {
            VaadinSession.getCurrent().setAttribute("token", null);
        }
        UI.getCurrent().navigate("login");
    }
}