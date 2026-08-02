package com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository;

import com.google.cloud.spring.data.datastore.repository.DatastoreRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.UserDocument;

public interface UserDocumentRepository extends DatastoreRepository<UserDocument, String> {
}
