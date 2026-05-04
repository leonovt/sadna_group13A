package com.sadna.group13a.application.DTO;

public record SystemAnalyticsDTO(
    int totalUsers,
    int activeQueues,
    int activeCompanies,
    int publishedEvents
) {}
