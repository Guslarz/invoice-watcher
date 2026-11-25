package com.guslarz.invoicewatcher.dao.mailattachment.firestore.configuration;

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.guslarz.invoicewatcher.dao.mailattachment.api.properties.AttachmentDaoProperties;
import com.guslarz.invoicewatcher.dao.mailattachment.firestore.properties.AttachmentFirestoreProperties;
import com.guslarz.invoicewatcher.dao.mailattachment.firestore.service.FirestoreAttachmentCaptureDao;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = AttachmentDaoProperties.Constants.PREFIX,
    name = AttachmentDaoProperties.Constants.MODE,
    havingValue = "firestore")
@EnableConfigurationProperties(AttachmentFirestoreProperties.class)
@Import(FirestoreAttachmentCaptureDao.class)
public class AttachmentFirestoreAutoConfiguration {
  @Bean("attachmentDatastore")
  Datastore attachmentDatastore(AttachmentFirestoreProperties properties) {
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
