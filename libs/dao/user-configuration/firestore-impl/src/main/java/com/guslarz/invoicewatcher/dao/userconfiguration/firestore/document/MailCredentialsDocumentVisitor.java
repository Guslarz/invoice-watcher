package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document;

public interface MailCredentialsDocumentVisitor<T> {

  default T visit(MailCredentialsDocument document) {
    return document.accept(this);
  }

  T visitBasicAuthMailCredentialsDocument(MailCredentialsDocument document);

  T visitGmailOauthCredentialsDocument(MailCredentialsDocument document);
}
