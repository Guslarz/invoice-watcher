package com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository;

import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  void deleteByName(String name);
}
