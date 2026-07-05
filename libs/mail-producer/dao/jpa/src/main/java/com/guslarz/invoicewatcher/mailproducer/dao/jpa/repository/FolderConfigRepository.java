package com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository;

import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.FolderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderConfigRepository extends JpaRepository<FolderConfigEntity, Long> {
}
