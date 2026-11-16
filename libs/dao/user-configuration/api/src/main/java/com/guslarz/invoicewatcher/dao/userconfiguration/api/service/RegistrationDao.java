package com.guslarz.invoicewatcher.dao.userconfiguration.api.service;

import com.guslarz.invoicewatcher.dao.userconfiguration.api.entity.user.User;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Atomic owner-scoped writes to the same configuration queried by the scanner. */
public interface RegistrationDao {

  Optional<User> find(String email, String ownerSubject);

  User create(User user);

  User update(String email, String ownerSubject, long version, UnaryOperator<User> change);

  void delete(String email, String ownerSubject, long version);
}
