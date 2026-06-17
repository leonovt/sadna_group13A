package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import com.sadna.group13a.domain.Aggregates.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
