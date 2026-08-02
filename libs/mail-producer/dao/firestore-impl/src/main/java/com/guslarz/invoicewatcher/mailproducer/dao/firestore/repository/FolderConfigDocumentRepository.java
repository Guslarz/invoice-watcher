package com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository;

import com.google.cloud.spring.data.datastore.repository.DatastoreRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.FolderConfigDocument;

public interface FolderConfigDocumentRepository extends DatastoreRepository<FolderConfigDocument, String> {
}
