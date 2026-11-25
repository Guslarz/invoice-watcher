package com.guslarz.invoicewatcher.eventprocessor.impl.service;

import com.guslarz.invoicewatcher.dao.eventprocessing.api.service.ProcessingDao;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

@DisplayName("PROCESSOR-017: SQL backend retains the complete processing runtime contract")
class JpaProcessorIntegrationTest extends ProcessorIntegrationTest {
  private AnnotationConfigApplicationContext context;

  @Override
  protected ProcessingDao processingDao(String name) {
    context = new AnnotationConfigApplicationContext();
    context.register(TestApplication.class);
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "invoice-watcher.event-processing.dao.mode", "jpa",
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.url",
                        "jdbc:h2:mem:" + name,
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.username", "sa",
                    "invoice-watcher.event-processing.dao.jpa.data-source.basic.driver",
                        "org.h2.Driver",
                    "invoice-watcher.event-processing.dao.jpa.ddl-auto", "create-drop",
                    "invoice-watcher.event-processing.dao.jpa.dialect",
                        "org.hibernate.dialect.H2Dialect")));
    context.refresh();
    return context.getBean(ProcessingDao.class);
  }

  @Override
  protected void closePersistence() {
    if (context != null) {
      context.close();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {}
}
