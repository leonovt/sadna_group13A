package com.sadna.group13a.presentation.views.notifications;

import com.sadna.group13a.application.Interfaces.IAuth;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.UserNotificationService;
import com.sadna.group13a.infrastructure.UserNotification;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationsPresenter {

    private final UserNotificationService userNotificationService;
    private final CompanyService companyService;
    private final IAuth auth;

    public NotificationsPresenter(UserNotificationService userNotificationService,
                                   CompanyService companyService,
                                   IAuth auth) {
        this.userNotificationService = userNotificationService;
        this.companyService = companyService;
        this.auth = auth;
    }

    public boolean isTokenValid(String token) {
        return token != null && auth.validateToken(token);
    }

    public String getUserId(String token) {
        return auth.extractUserId(token);
    }

    public String getToken() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? null : (String) session.getAttribute("token");
    }

    public List<UserNotification> getNotifications(String userId) {
        return userNotificationService.getForUser(userId);
    }

    public Result<Void> acceptNomination(String token, String companyId) {
        Result<Void> result = companyService.acceptNomination(token, companyId);
        if (result.isSuccess()) {
            // Clear the persisted invitation so it does not reappear on refresh (issue #368).
            userNotificationService.dismissNomination(auth.extractUserId(token), companyId);
        }
        return result;
    }

    public Result<Void> rejectNomination(String token, String companyId) {
        Result<Void> result = companyService.rejectNomination(token, companyId);
        if (result.isSuccess()) {
            // Clear the persisted invitation so it does not reappear on refresh (issue #368).
            userNotificationService.dismissNomination(auth.extractUserId(token), companyId);
        }
        return result;
    }

    public void dismiss(String notificationId) {
        userNotificationService.dismiss(notificationId);
    }
}
