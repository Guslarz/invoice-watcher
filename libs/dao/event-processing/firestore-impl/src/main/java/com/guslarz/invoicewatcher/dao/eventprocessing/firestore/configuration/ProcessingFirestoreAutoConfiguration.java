package com.guslarz.invoicewatcher.dao.eventprocessing.firestore.configuration;

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.guslarz.invoicewatcher.dao.eventprocessing.api.properties.ProcessingDaoProperties;
import com.guslarz.invoicewatcher.dao.eventprocessing.firestore.properties.ProcessingFirestoreProperties;
import com.guslarz.invoicewatcher.dao.eventprocessing.firestore.service.FirestoreProcessingDao;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = ProcessingDaoProperties.Constants.PREFIX,
    name = ProcessingDaoProperties.Constants.MODE,
    havingValue = "firestore")
@EnableConfigurationProperties(ProcessingFirestoreProperties.class)
@Import(FirestoreProcessingDao.class)
public class ProcessingFirestoreAutoConfiguration {
  @Bean("processingDatastore")
  Datastore datastore(ProcessingFirestoreProperties properties) {
    var builder =
        DatastoreOptions.newBuilder()
            .setProjectId(properties.projectId())
            .setDatabaseId(properties.databaseId())
            .setNamespace(properties.namespace());
    if (properties.host() != null && !properties.host().isBlank()) {
      builder.setHost(properties.host()).setCredentials(NoCredentials.getInstance());
    }
    return builder.build().getService();
  }
}
