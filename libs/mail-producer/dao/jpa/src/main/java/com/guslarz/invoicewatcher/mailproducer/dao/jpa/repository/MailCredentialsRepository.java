package com.guslarz.invoicewatcher.mailproducer.dao.jpa.repository;

import com.guslarz.invoicewatcher.mailproducer.dao.jpa.entity.mailcredentials.MailCredentialsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailCredentialsRepository extends JpaRepository<MailCredentialsEntity, Long> {
}
