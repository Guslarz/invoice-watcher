package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.repository;

import com.google.cloud.spring.data.datastore.repository.DatastoreRepository;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.UserDocument;

public interface UserDocumentRepository extends DatastoreRepository<UserDocument, String> {}
