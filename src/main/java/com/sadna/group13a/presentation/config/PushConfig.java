package com.sadna.group13a.presentation.config;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.communication.PushMode;
import org.springframework.stereotype.Component;

@Component
public class PushConfig implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
            uiEvent.getUI().getPushConfiguration().setPushMode(PushMode.AUTOMATIC)
        );
    }
}
