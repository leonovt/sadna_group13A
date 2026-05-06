package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import java.util.List;
import java.util.Optional;

public interface IAdminRepository {
    void save(Admin admin);
    Optional<Admin> findById(String id);
    Optional<Admin> findByUserId(String userId);
    List<Admin> findAll();
    void delete(String id);
    boolean hasAtLeastOneAdmin();
}
