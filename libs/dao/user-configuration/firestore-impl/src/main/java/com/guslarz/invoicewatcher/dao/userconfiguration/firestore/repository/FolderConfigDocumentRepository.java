package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.repository;

import com.google.cloud.spring.data.datastore.repository.DatastoreRepository;
import com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document.FolderConfigDocument;

public interface FolderConfigDocumentRepository
    extends DatastoreRepository<FolderConfigDocument, String> {}
