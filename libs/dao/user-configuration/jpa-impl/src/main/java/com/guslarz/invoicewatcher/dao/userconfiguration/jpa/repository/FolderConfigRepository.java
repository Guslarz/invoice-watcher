package com.guslarz.invoicewatcher.dao.userconfiguration.jpa.repository;

import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.FolderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FolderConfigRepository extends JpaRepository<FolderConfigEntity, Long> {
  @Modifying
  @Query("update FolderConfigEntity f set f.uidValidity = ?2, f.lastUid = ?3 where f.id = ?1")
  int advance(long id, long validity, long uid);
}
