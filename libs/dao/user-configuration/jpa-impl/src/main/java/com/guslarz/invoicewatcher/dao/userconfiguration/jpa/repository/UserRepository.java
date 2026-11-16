package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository;

import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<UserEntity> findByName(String name);

  Optional<UserEntity> findByOwnerSubject(String ownerSubject);

  void deleteByName(String name);
}
