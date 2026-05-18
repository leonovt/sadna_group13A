package com.sadna.group13a.presentation.views.queue;

import com.sadna.group13a.application.Services.QueueService;
import org.springframework.stereotype.Component;

@Component
public class QueuePresenter {

    private final QueueService queueService;

    public QueuePresenter(QueueService queueService) {
        this.queueService = queueService;
    }
}
