/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.views.user;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.server.StreamResource;
import de.timesnake.web.data.entity.User;
import de.timesnake.web.security.AuthenticatedUser;

import java.io.ByteArrayInputStream;
import java.util.Optional;

public class UserMenu extends Div {

  public UserMenu(AuthenticatedUser authenticatedUser) {
    Optional<User> maybeUser = authenticatedUser.get();
    if (maybeUser.isPresent()) {
      User user = maybeUser.get();

      Avatar avatar = new Avatar(user.getName());
      StreamResource resource = new StreamResource("profile-pic",
          () -> new ByteArrayInputStream(user.getProfilePicture()));
      avatar.setImageResource(resource);
      avatar.setThemeName("xsmall");
      avatar.getElement().setAttribute("tabindex", "-1");

      MenuBar userMenu = new MenuBar();
      userMenu.setThemeName("tertiary-inline contrast");

      MenuItem userName = userMenu.addItem("");
      Div div = new Div();
      div.add(avatar);
      div.add(user.getName());
      div.add(new Icon("lumo", "dropdown"));
      div.getElement().getStyle().set("display", "flex");
      div.getElement().getStyle().set("align-items", "center");
      div.getElement().getStyle().set("gap", "var(--lumo-space-s)");
      userName.add(div);
      userName.getSubMenu().addItem("Sign out", e -> authenticatedUser.logout());

      this.add(userMenu);
    } else {
      Anchor loginLink = new Anchor("login", "Sign in");
      this.add(loginLink);
    }
  }
}
