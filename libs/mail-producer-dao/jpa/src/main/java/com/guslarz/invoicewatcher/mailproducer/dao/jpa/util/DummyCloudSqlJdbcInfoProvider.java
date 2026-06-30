package com.guslarz.invoicewatcher.mailproducer.dao.jpa.util;

import com.google.cloud.spring.autoconfigure.sql.CloudSqlJdbcInfoProvider;
import com.guslarz.invoicewatcher.mailproducer.dao.jpa.properties.MailProducerDaoJpaProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DummyCloudSqlJdbcInfoProvider implements CloudSqlJdbcInfoProvider {

  private final MailProducerDaoJpaProperties properties;

  @Override
  public String getJdbcDriverClass() {
    return properties.dataSource().basic().driver();
  }

  @Override
  public String getJdbcUrl() {
    return properties.dataSource().basic().url();
  }
}
