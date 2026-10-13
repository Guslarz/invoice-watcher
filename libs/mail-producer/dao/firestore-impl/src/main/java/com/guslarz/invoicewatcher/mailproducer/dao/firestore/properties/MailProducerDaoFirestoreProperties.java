package com.guslarz.invoicewatcher.mailproducer.dao.firestore.properties;

import com.guslarz.invoicewatcher.mailproducer.dao.api.MailProducerDaoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore storage of users, active when `invoice-watcher.mail-producer.dao.mode` is `firestore`.
 *
 * @param databaseId Id of the Firestore database (in Datastore mode).
 * @param host Emulator `host:port`; when set, the emulator is used without credentials.
 */
@ConfigurationProperties(MailProducerDaoProperties.Constants.Prefix + ".firestore")
public record MailProducerDaoFirestoreProperties(
        String databaseId,
        String host
) {
}