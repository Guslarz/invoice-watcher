package com.guslarz.invoicewatcher.dao.mailattachment.firestore.properties;

import com.guslarz.invoicewatcher.dao.mailattachment.api.properties.AttachmentDaoProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Independently activated capture persistence.
 *
 * @param projectId Required in firestore mode. Google Cloud or emulator project.
 * @param databaseId Optional in firestore mode. Datastore database identifier.
 * @param namespace Optional in firestore mode. Isolated capture namespace.
 * @param host Optional in firestore mode. Explicit emulator endpoint without credentials.
 */
@Validated
@ConfigurationProperties(AttachmentDaoProperties.Constants.PREFIX + ".firestore")
public record AttachmentFirestoreProperties(
    @NotBlank String projectId,
    @DefaultValue("(default)") @NotBlank String databaseId,
    @DefaultValue("mail-attachments") @NotBlank String namespace,
    String host) {}
