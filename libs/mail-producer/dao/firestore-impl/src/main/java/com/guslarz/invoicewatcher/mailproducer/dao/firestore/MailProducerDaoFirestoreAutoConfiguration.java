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
import com.google.cloud.spring.data.datastore.core.convert.*;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import com.google.cloud.spring.data.datastore.repository.config.EnableDatastoreRepositories;
import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper.FirestoreMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.properties.MailProducerDaoFirestoreProperties;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.FolderConfigDaoServiceFirestoreImpl;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.service.UserDaoServiceFirestoreImpl;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.IOException;

@AutoConfiguration
@Import({
        UserDaoServiceFirestoreImpl.class,
        FolderConfigDaoServiceFirestoreImpl.class,
        FirestoreMapper.class
})
@EnableDatastoreRepositories(
        datastoreTemplateRef = MailProducerDaoFirestoreAutoConfiguration.Beans.DatastoreTemplate,
        datastoreMappingContextRef = MailProducerDaoFirestoreAutoConfiguration.Beans.DatastoreMappingContext
)
@ConditionalOnProperty(
        prefix = MailProducerDaoProperties.Constants.Prefix,
        name = MailProducerDaoProperties.Constants.Mode,
        havingValue = "firestore"
)
@EnableConfigurationProperties(MailProducerDaoFirestoreProperties.class)
public class MailProducerDaoFirestoreAutoConfiguration {

    @Bean(Beans.ProjectIdProvider)
    GcpProjectIdProvider projectIdProvider() {
        return new DefaultGcpProjectIdProvider();
    }

    @Bean(Beans.CredentialsProvider)
    CredentialsProvider credentialsProvider() throws IOException {
        return new DefaultCredentialsProvider(Credentials::new);
    }

    @Bean(Beans.Datastore)
    Datastore datastore(
            MailProducerDaoFirestoreProperties properties,
            @Qualifier(Beans.ProjectIdProvider) GcpProjectIdProvider projectIdProvider,
            @Qualifier(Beans.CredentialsProvider) CredentialsProvider credentialsProvider) throws IOException {
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

    @Bean(Beans.DatastoreProvider)
    DatastoreProvider datastoreProvider(@Qualifier(Beans.Datastore) Datastore datastore) {
        return () -> datastore;
    }

    @Bean(Beans.DatastoreMappingContext)
    DatastoreMappingContext datastoreMappingContext() {
        return new DatastoreMappingContext();
    }

    @Bean(Beans.DatastoreCustomConversions)
    DatastoreCustomConversions datastoreCustomConversions() {
        return new DatastoreCustomConversions();
    }

    @Bean(Beans.ObjectToKeyFactory)
    ObjectToKeyFactory objectToKeyFactory(@Qualifier(Beans.DatastoreProvider) DatastoreProvider datastoreProvider) {
        return new DatastoreServiceObjectToKeyFactory(datastoreProvider);
    }

    @Bean(Beans.ReadWriteConversions)
    ReadWriteConversions readWriteConversions(
            @Qualifier(Beans.DatastoreCustomConversions) DatastoreCustomConversions customConversions,
            @Qualifier(Beans.ObjectToKeyFactory) ObjectToKeyFactory objectToKeyFactory,
            @Qualifier(Beans.DatastoreMappingContext) DatastoreMappingContext mappingContext
    ) {
        return new TwoStepsConversions(customConversions, objectToKeyFactory, mappingContext);
    }

    @Bean(Beans.DatastoreEntityConverter)
    DatastoreEntityConverter datastoreEntityConverter(
            @Qualifier(Beans.DatastoreMappingContext) DatastoreMappingContext datastoreMappingContext,
            @Qualifier(Beans.ReadWriteConversions) ReadWriteConversions conversions) {
        return new DefaultDatastoreEntityConverter(datastoreMappingContext, conversions);
    }

    @Bean(Beans.DatastoreTemplate)
    DatastoreTemplate datastoreTemplate(
            @Qualifier(Beans.DatastoreProvider) DatastoreProvider datastoreProvider,
            @Qualifier(Beans.DatastoreMappingContext) DatastoreMappingContext datastoreMappingContext,
            @Qualifier(Beans.DatastoreEntityConverter) DatastoreEntityConverter datastoreEntityConverter,
            @Qualifier(Beans.ObjectToKeyFactory) ObjectToKeyFactory objectToKeyFactory) {
        return new DatastoreTemplate(datastoreProvider, datastoreEntityConverter, datastoreMappingContext, objectToKeyFactory);
    }

    @Bean(Beans.DatastoreTransactionManager)
    DatastoreTransactionManager transactionManager(@Qualifier(Beans.DatastoreProvider) DatastoreProvider datastoreProvider) {
        return new DatastoreTransactionManager(datastoreProvider);
    }

    @UtilityClass
    static class Beans {

        public static final String ProjectIdProvider = "daoFirestoreProjectIdProvider";
        public static final String CredentialsProvider = "daoFirestoreCredentialsProvider";
        public static final String Datastore = "daoFirestoreDatastore";
        public static final String DatastoreProvider = "daoFirestoreDatastoreProvider";
        public static final String DatastoreMappingContext = "daoFirestoreDatastoreMappingContext";
        public static final String DatastoreCustomConversions = "daoFirestoreDatastoreCustomConversions";
        public static final String ObjectToKeyFactory = "daoFirestoreObjectToKeyFactory";
        public static final String ReadWriteConversions = "daoFirestoreReadWriteConversions";
        public static final String DatastoreEntityConverter = "daoFirestoreDatastoreEntityConverter";
        public static final String DatastoreTemplate = "daoFirestoreDatastoreTemplate";
        public static final String DatastoreTransactionManager = "daoFirestoreDatastoreTransactionManager";
    }
}
