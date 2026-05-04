package com.sadna.group13a.domain.Interfaces;

import java.util.List;
import java.util.Optional;

import com.sadna.group13a.domain.Aggregates.User.Admin;

public interface IAdminRepository {
    void save(Admin admin);
    Optional<Admin> findById(String id);
    List<Admin> findAll();
    void delete(String id);
    boolean hasAtLeastOneAdmin();
}
