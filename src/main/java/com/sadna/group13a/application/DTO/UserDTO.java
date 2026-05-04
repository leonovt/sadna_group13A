package com.sadna.group13a.application.DTO;

import com.sadna.group13a.domain.Aggregates.User.UserRole;

/**
 * Data Transfer Object for a User Profile.
 */
public record UserDTO(
    String username,
    UserRole role
) {}
