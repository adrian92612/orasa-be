package com.orasa.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByUsername(String username);
  Optional<User> findByEmail(String email);
  List<User> findByBusinessId(UUID businessId);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);

  @Query("SELECT u FROM User u LEFT JOIN FETCH u.business LEFT JOIN FETCH u.branches WHERE u.id = :id")
  Optional<User> findByIdWithRelations(@Param("id") UUID id);
}
