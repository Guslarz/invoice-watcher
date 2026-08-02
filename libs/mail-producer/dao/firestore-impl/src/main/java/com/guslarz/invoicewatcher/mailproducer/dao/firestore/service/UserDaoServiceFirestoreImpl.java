package com.guslarz.invoicewatcher.mailproducer.dao.firestore.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.FolderConfig;
import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;
import com.guslarz.invoicewatcher.mailproducer.dao.api.service.UserDaoService;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.FolderConfigDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.document.UserDocument;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.mapper.FirestoreMapper;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.FolderConfigDocumentRepository;
import com.guslarz.invoicewatcher.mailproducer.dao.firestore.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class UserDaoServiceFirestoreImpl implements UserDaoService {

  private final UserDocumentRepository userRepository;
  private final FolderConfigDocumentRepository folderRepository;
  private final FirestoreMapper mapper;

  @Override
  public List<User> findAll() {
    List<FolderConfigDocument> allFolders = StreamSupport
        .stream(folderRepository.findAll().spliterator(), false)
        .toList();

    return StreamSupport
        .stream(userRepository.findAll().spliterator(), false)
        .map(userDoc -> mapper.toUser(userDoc, allFolders))
        .toList();
  }

  @Override
  public void add(User user) {
    User userWithFolderIds = assignFolderIds(user);
    saveFolders(userWithFolderIds);

    UserDocument doc = mapper.toUserDocument(userWithFolderIds);
    userRepository.save(doc);
  }

  @Override
  public void upsertUserByName(User user) {
    deleteUserByName(user.name());

    User userWithFolderIds = assignFolderIds(user);
    saveFolders(userWithFolderIds);

    UserDocument doc = mapper.toUserDocument(userWithFolderIds);
    userRepository.save(doc);
  }

  private void deleteUserByName(String name) {
    userRepository.findById(name).ifPresent(existing -> {
      if (existing.getScanConfigs() != null) {
        for (var scanConfig : existing.getScanConfigs()) {
          if (scanConfig.getFolderIds() != null) {
            folderRepository.deleteAllById(scanConfig.getFolderIds());
          }
        }
      }
      userRepository.delete(existing);
    });
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

  private void saveFolders(User user) {
    for (var scanConfig : user.scanConfigs()) {
      for (FolderConfig folder : scanConfig.folders()) {
        FolderConfigDocument doc = mapper.toFolderConfigDocument(folder);
        folderRepository.save(doc);
      }
    }
  }
}
