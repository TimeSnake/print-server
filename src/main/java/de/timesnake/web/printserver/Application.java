/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import de.timesnake.web.printserver.data.Role;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.data.service.UserRepository;
import de.timesnake.web.printserver.security.SecurityConfiguration;
import de.timesnake.web.printserver.util.Config;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Set;
import java.util.logging.Logger;

@SpringBootApplication
@Theme(value = "light")
@PWA(name = "PrintServer", shortName = "PrintServer")
@Push(PushMode.MANUAL)
public class Application implements AppShellConfigurator {

  public static Logger getLogger() {
    return Logger.getLogger("print-server");
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public DataSource datasource(Config config) {
    final DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
    dataSource.setUrl(config.getDatabaseUrl());
    dataSource.setUsername(config.getDatabaseUser());
    dataSource.setPassword(config.getDatabasePassword());
    return dataSource;
  }

  @Bean
  public CommandLineRunner loadData(SecurityConfiguration securityConfiguration, UserRepository userRepository) {
    return (args) -> {
      if (userRepository.count() == 0) {
        User adminUser = new User();
        adminUser.setUsername("admin");
        adminUser.setName("admin");
        adminUser.setRoles(Set.of(Role.ADMIN));
        adminUser.setHashedPassword(securityConfiguration.passwordEncoder().encode("admin"));
        userRepository.saveAndFlush(adminUser);
      }
    };
  }
}

