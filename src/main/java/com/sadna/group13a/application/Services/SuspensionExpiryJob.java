package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Aggregates.User.User;
import com.sadna.group13a.domain.Events.UserReactivatedEvent;
import com.sadna.group13a.domain.Interfaces.IUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically lifts temporary suspensions whose end date has passed.
 * Runs every minute; @EnableScheduling is already present on SadnaApplication.
 */
@Component
public class SuspensionExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(SuspensionExpiryJob.class);

    private final IUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemLogService systemLogService;

    public SuspensionExpiryJob(IUserRepository userRepository,
                                ApplicationEventPublisher eventPublisher,
                                SystemLogService systemLogService) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.systemLogService = systemLogService;
    }

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void liftExpiredSuspensions() {
        LocalDateTime now = LocalDateTime.now();

        List<User> expired = userRepository.findAll().stream()
                .filter(u -> u.isSuspended()
                        && u.getSuspendedUntil() != null
                        && u.getSuspendedUntil().isBefore(now))
                .toList();

        for (User user : expired) {
            user.activate();
            userRepository.save(user);
            eventPublisher.publishEvent(new UserReactivatedEvent(user.getId(), "system"));
            systemLogService.logEvent("suspensionExpired userId=" + user.getId() + " username=" + user.getUsername());
            logger.info("Suspension expired and lifted automatically for user '{}'.", user.getUsername());
        }
    }
}
