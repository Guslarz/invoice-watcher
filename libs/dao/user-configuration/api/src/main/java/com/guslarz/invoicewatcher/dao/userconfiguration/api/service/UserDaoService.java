package com.guslarz.invoicewatcher.dao.userconfiguration.api.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import java.util.List;

public interface UserDaoService {

  List<User> findAll();

  void add(User user);

  void upsertUserByName(User user);
}
