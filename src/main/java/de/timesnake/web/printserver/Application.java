/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.logging.Logger;

@SpringBootApplication
@Theme(value = "light")
@PWA(name = "WebApp", shortName = "WebApp")
@Push(PushMode.MANUAL)
public class Application implements AppShellConfigurator {

  public static Logger getLogger() {
    return Logger.getLogger("print-server");
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

}

