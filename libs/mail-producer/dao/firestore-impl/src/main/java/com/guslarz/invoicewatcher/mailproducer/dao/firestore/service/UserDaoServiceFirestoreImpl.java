package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper.FirestoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
public class UserDaoServiceFirestoreImpl implements UserDaoService {

  private static final String USERS_COLLECTION = "mail-producer-users";
  private static final String FOLDERS_COLLECTION = "mail-producer-folders";

  private final Firestore firestore;
  private final FirestoreMapper mapper;

  @Override
  public List<User> findAll() {
    try {
      CollectionReference usersCol = firestore.collection(USERS_COLLECTION);
      List<QueryDocumentSnapshot> userDocs = usersCol.get().get().getDocuments();

      List<FolderConfigDocument> allFolders = firestore.collection(FOLDERS_COLLECTION)
          .get().get().getDocuments().stream()
          .map(doc -> doc.toObject(FolderConfigDocument.class))
          .toList();

      return userDocs.stream()
          .map(doc -> doc.toObject(UserDocument.class))
          .map(userDoc -> mapper.toUser(userDoc, allFolders))
          .toList();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while fetching users", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to fetch users", e);
    }
  }

  @Override
  public void add(User user) {
    try {
      User userWithFolderIds = assignFolderIds(user);
      saveFolders(userWithFolderIds);

      UserDocument doc = mapper.toUserDocument(userWithFolderIds);
      firestore.collection(USERS_COLLECTION).document(doc.getId()).set(doc).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while adding user", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to add user", e);
    }
  }

  @Override
  public void upsertUserByName(User user) {
    try {
      deleteUserByName(user.name());

      User userWithFolderIds = assignFolderIds(user);
      saveFolders(userWithFolderIds);

      UserDocument doc = mapper.toUserDocument(userWithFolderIds);
      firestore.collection(USERS_COLLECTION).document(doc.getId()).set(doc).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while upserting user", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to upsert user", e);
    }
  }

  private void deleteUserByName(String name) throws ExecutionException, InterruptedException {
    DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(name);
    UserDocument existing = userRef.get().get().toObject(UserDocument.class);
    if (existing != null && existing.getScanConfigs() != null) {
      for (var scanConfig : existing.getScanConfigs()) {
        if (scanConfig.getFolderIds() != null) {
          for (String folderId : scanConfig.getFolderIds()) {
            firestore.collection(FOLDERS_COLLECTION).document(folderId).delete().get();
          }
        }
      }
    }
    userRef.delete().get();
  }

  private User assignFolderIds(User user) {
    return User.builder()
        .id(user.id())
        .name(user.name())
        .scanConfigs(user.scanConfigs().stream()
            .map(scanConfig -> scanConfig.toBuilder()
                .folders(scanConfig.folders().stream()
                    .map(folder -> folder.id() != null ? folder : folder.toBuilder()
                        .id(UUID.randomUUID().toString())
                        .build())
                    .toList())
                .build())
            .toList())
        .build();
  }

  private void saveFolders(User user) throws ExecutionException, InterruptedException {
    CollectionReference foldersCol = firestore.collection(FOLDERS_COLLECTION);
    for (var scanConfig : user.scanConfigs()) {
      for (FolderConfig folder : scanConfig.folders()) {
        FolderConfigDocument doc = mapper.toFolderConfigDocument(folder);
        foldersCol.document(folder.id()).set(doc).get();
      }
    }
  }
}
