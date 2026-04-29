package com.sadna.group13a.application.dto;

import com.sadna.group13a.domain.shared.UserRole;
import com.sadna.group13a.domain.shared.UserState;

/**
 * Data Transfer Object for a User Profile.
 */
public record UserDTO(
    String id,
    String username,
    UserRole role,
    UserState state
) {}
