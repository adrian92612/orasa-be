package com.orasa.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orasa.backend.domain.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByUsername(String username);
  Optional<UserEntity> findByEmail(String email);
  List<UserEntity> findByBusinessId(UUID businessId);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);

  @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.business LEFT JOIN FETCH u.branches WHERE u.id = :id")
  Optional<UserEntity> findByIdWithRelations(@Param("id") UUID id);

  @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.business LEFT JOIN FETCH u.branches WHERE u.email = :email")
  Optional<UserEntity> findByEmailWithRelations(@Param("email") String email);

  @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.business LEFT JOIN FETCH u.branches WHERE u.username = :username")
  Optional<UserEntity> findByUsernameWithRelations(@Param("username") String username);
}
