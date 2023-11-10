/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver.data.service;

import de.timesnake.web.printserver.data.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  User findByUsername(String username);
}
