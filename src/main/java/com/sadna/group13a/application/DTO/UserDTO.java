package com.sadna.group13a.application.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sadna.group13a.domain.Aggregates.User.UserRole;

/**
 * Data Transfer Object for a User Profile.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDTO(
    String username,
    UserRole role
) {}
