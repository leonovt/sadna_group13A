
package com.sadna.group13a.presentation.notification;

import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationBroadcaster {

    private final Map<String, UI> sessions = new ConcurrentHashMap<>();

    public void register(String userId, UI ui) {
        sessions.put(userId, ui);
    }

    public void unregister(String userId) {
        sessions.remove(userId);
    }

    public void send(String userId, String message) {
        UI ui = sessions.get(userId);
        if (ui != null) {
            ui.access(() -> ui.getPage().executeJs(
                "window.dispatchEvent(new CustomEvent('notification', { detail: $0 }))", message
            ));
        }
    }
}
