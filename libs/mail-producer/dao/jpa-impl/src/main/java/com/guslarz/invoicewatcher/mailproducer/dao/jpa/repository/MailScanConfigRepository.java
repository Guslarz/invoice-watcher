package com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository;

import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.MailScanConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailScanConfigRepository extends JpaRepository<MailScanConfigEntity, Long> {
}
