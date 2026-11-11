package com.guslarz.invoicewatcher.mailproducer.dao.firestore;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.spring.autoconfigure.datastore.DatastoreProvider;
import com.google.cloud.spring.core.Credentials;
import com.google.cloud.spring.core.DefaultCredentialsProvider;
import com.google.cloud.spring.core.DefaultGcpProjectIdProvider;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.google.cloud.spring.data.datastore.core.DatastoreTransactionManager;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreCustomConversions;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreEntityConverter;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreServiceObjectToKeyFactory;
import com.google.cloud.spring.data.datastore.core.convert.DefaultDatastoreEntityConverter;
import com.google.cloud.spring.data.datastore.core.convert.ObjectToKeyFactory;
import com.google.cloud.spring.data.datastore.core.convert.ReadWriteConversions;
import com.google.cloud.spring.data.datastore.core.convert.TwoStepsConversions;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import com.google.cloud.spring.data.datastore.repository.config.EnableDatastoreRepositories;
import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper.FirestoreMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.properties.MailProducerDaoFirestoreProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.FolderConfigDaoServiceFirestoreImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.UserDaoServiceFirestoreImpl;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@AutoConfiguration
@Import({
    UserDaoServiceFirestoreImpl.class,
    FolderConfigDaoServiceFirestoreImpl.class,
    FirestoreMapper.class
})
@EnableDatastoreRepositories(
    datastoreTemplateRef = MailProducerDaoFirestoreAutoConfiguration.Beans.DATASTORE_TEMPLATE,
    datastoreMappingContextRef =
        MailProducerDaoFirestoreAutoConfiguration.Beans.DATASTORE_MAPPING_CONTEXT
)
@ConditionalOnProperty(
    prefix = MailProducerDaoProperties.Constants.PREFIX,
    name = MailProducerDaoProperties.Constants.MODE,
    havingValue = "firestore"
)
@EnableConfigurationProperties(MailProducerDaoFirestoreProperties.class)
public class MailProducerDaoFirestoreAutoConfiguration {

  @PostConstruct
  public void init() {
    log.info("Configuring mail producer dao for firestore");
  }

  @Bean(Beans.PROJECT_ID_PROVIDER)
  GcpProjectIdProvider projectIdProvider() {
    return new DefaultGcpProjectIdProvider();
  }

  @Bean(Beans.CREDENTIALS_PROVIDER)
  CredentialsProvider credentialsProvider() throws IOException {
    return new DefaultCredentialsProvider(Credentials::new);
  }

  @Bean(Beans.DATASTORE)
  Datastore datastore(
      MailProducerDaoFirestoreProperties properties,
      @Qualifier(Beans.PROJECT_ID_PROVIDER) GcpProjectIdProvider projectIdProvider,
      @Qualifier(Beans.CREDENTIALS_PROVIDER) CredentialsProvider credentialsProvider)
      throws IOException {
    DatastoreOptions.Builder builder = DatastoreOptions.newBuilder()
        .setProjectId(projectIdProvider.getProjectId());
    if (StringUtils.isNotBlank(properties.host())) {
      builder.setHost(properties.host());
      builder.setCredentials(NoCredentials.getInstance());
    } else {
      builder.setDatabaseId(properties.databaseId());
      builder.setCredentials(credentialsProvider.getCredentials());
    }
    return builder.build().getService();
  }

  @Bean(Beans.DATASTORE_PROVIDER)
  DatastoreProvider datastoreProvider(@Qualifier(Beans.DATASTORE) Datastore datastore) {
    return () -> datastore;
  }

  @Bean(Beans.DATASTORE_MAPPING_CONTEXT)
  DatastoreMappingContext datastoreMappingContext() {
    return new DatastoreMappingContext();
  }

  @Bean(Beans.DATASTORE_CUSTOM_CONVERSIONS)
  DatastoreCustomConversions datastoreCustomConversions() {
    return new DatastoreCustomConversions();
  }

  @Bean(Beans.OBJECT_TO_KEY_FACTORY)
  ObjectToKeyFactory objectToKeyFactory(
      @Qualifier(Beans.DATASTORE_PROVIDER) DatastoreProvider datastoreProvider) {
    return new DatastoreServiceObjectToKeyFactory(datastoreProvider);
  }

  @Bean(Beans.READ_WRITE_CONVERSIONS)
  ReadWriteConversions readWriteConversions(
      @Qualifier(Beans.DATASTORE_CUSTOM_CONVERSIONS) DatastoreCustomConversions customConversions,
      @Qualifier(Beans.OBJECT_TO_KEY_FACTORY) ObjectToKeyFactory objectToKeyFactory,
      @Qualifier(Beans.DATASTORE_MAPPING_CONTEXT) DatastoreMappingContext mappingContext
  ) {
    return new TwoStepsConversions(customConversions, objectToKeyFactory, mappingContext);
  }

  @Bean(Beans.DATASTORE_ENTITY_CONVERTER)
  DatastoreEntityConverter datastoreEntityConverter(
      @Qualifier(Beans.DATASTORE_MAPPING_CONTEXT) DatastoreMappingContext datastoreMappingContext,
      @Qualifier(Beans.READ_WRITE_CONVERSIONS) ReadWriteConversions conversions) {
    return new DefaultDatastoreEntityConverter(datastoreMappingContext, conversions);
  }

  @Bean(Beans.DATASTORE_TEMPLATE)
  DatastoreTemplate datastoreTemplate(
      @Qualifier(Beans.DATASTORE_PROVIDER) DatastoreProvider datastoreProvider,
      @Qualifier(Beans.DATASTORE_MAPPING_CONTEXT) DatastoreMappingContext datastoreMappingContext,
      @Qualifier(Beans.DATASTORE_ENTITY_CONVERTER) DatastoreEntityConverter datastoreEntityConverter,
      @Qualifier(Beans.OBJECT_TO_KEY_FACTORY) ObjectToKeyFactory objectToKeyFactory) {
    return new DatastoreTemplate(
        datastoreProvider, datastoreEntityConverter, datastoreMappingContext, objectToKeyFactory);
  }

  @Bean(Beans.DATASTORE_TRANSACTION_MANAGER)
  DatastoreTransactionManager transactionManager(
      @Qualifier(Beans.DATASTORE_PROVIDER) DatastoreProvider datastoreProvider) {
    return new DatastoreTransactionManager(datastoreProvider);
  }

  @UtilityClass
  static class Beans {

    public static final String PROJECT_ID_PROVIDER = "daoFirestoreProjectIdProvider";
    public static final String CREDENTIALS_PROVIDER = "daoFirestoreCredentialsProvider";
    public static final String DATASTORE = "daoFirestoreDatastore";
    public static final String DATASTORE_PROVIDER = "daoFirestoreDatastoreProvider";
    public static final String DATASTORE_MAPPING_CONTEXT = "daoFirestoreDatastoreMappingContext";
    public static final String DATASTORE_CUSTOM_CONVERSIONS =
        "daoFirestoreDatastoreCustomConversions";
    public static final String OBJECT_TO_KEY_FACTORY = "daoFirestoreObjectToKeyFactory";
    public static final String READ_WRITE_CONVERSIONS = "daoFirestoreReadWriteConversions";
    public static final String DATASTORE_ENTITY_CONVERTER = "daoFirestoreDatastoreEntityConverter";
    public static final String DATASTORE_TEMPLATE = "daoFirestoreDatastoreTemplate";
    public static final String DATASTORE_TRANSACTION_MANAGER =
        "daoFirestoreDatastoreTransactionManager";
  }
}
