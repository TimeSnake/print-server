/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver.security;

import com.vaadin.flow.spring.security.AuthenticationContext;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.data.service.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class AuthenticatedUser {

  private final UserRepository userRepository;
  private final AuthenticationContext authenticationContext;

  public AuthenticatedUser(AuthenticationContext authenticationContext, UserRepository userRepository) {
    this.userRepository = userRepository;
    this.authenticationContext = authenticationContext;
  }

  @Transactional
  public Optional<User> get() {
    return authenticationContext.getAuthenticatedUser(UserDetails.class)
        .map(userDetails -> userRepository.findByUsername(userDetails.getUsername()));
  }

  public void logout() {
    authenticationContext.logout();
  }

}
