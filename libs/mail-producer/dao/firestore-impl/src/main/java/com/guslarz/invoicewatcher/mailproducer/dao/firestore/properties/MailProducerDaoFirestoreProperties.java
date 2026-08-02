package com.guslarz.invoicewatcher.mailproducer.dao.firestore.properties;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(MailProducerDaoProperties.Constants.Prefix + ".firestore")
public record MailProducerDaoFirestoreProperties(
        String databaseId,
        String host
) {
}