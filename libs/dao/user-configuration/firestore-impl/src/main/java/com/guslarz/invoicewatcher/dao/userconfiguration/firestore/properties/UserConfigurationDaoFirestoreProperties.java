package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.properties;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.UserConfigurationDaoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore storage of users, active when `invoice-watcher.user-configuration.dao.mode` is
 * `firestore`.
 *
 * @param databaseId Optional. Id of the Firestore database (in Datastore mode).
 * @param host Optional. Emulator `host:port`; when set, the emulator is used without credentials.
 */
@ConfigurationProperties(UserConfigurationDaoProperties.Constants.PREFIX + ".firestore")
public record UserConfigurationDaoFirestoreProperties(String databaseId, String host) {}
