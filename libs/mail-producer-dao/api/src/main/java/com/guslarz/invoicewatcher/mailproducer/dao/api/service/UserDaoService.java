package com.guslarz.invoicewatcher.mailproducer.dao.api.service;

import com.guslarz.invoicewatcher.mailproducer.dao.api.entity.user.User;

import java.util.List;

public interface UserDaoService {

  void add(User user);

  List<User> findAll();
}
