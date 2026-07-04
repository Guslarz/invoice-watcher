package com.guslarz.invoicewatcher.mailproducer.dao.api.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;

import java.util.List;

public interface UserDaoService {

  List<User> findAll();

  void add(User user);

  void upsertUserByName(User user);
}
