/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.views.user;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.server.StreamResource;
import de.timesnake.web.data.entity.User;
import de.timesnake.web.data.service.UserService;
import de.timesnake.web.security.AuthenticatedUser;
import de.timesnake.web.security.SecurityConfiguration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.Optional;

public class UserMenu extends Div {

  private final UserService userService;
  private final SecurityConfiguration securityConfiguration;

  private User user;

  private Dialog settingsDialog;

  public UserMenu(UserService userService, SecurityConfiguration securityConfiguration,
                  AuthenticatedUser authenticatedUser, boolean showSettings) {
    this.userService = userService;
    this.securityConfiguration = securityConfiguration;

    this.getStyle().setPadding("var(--lumo-space-s)");

    Optional<User> maybeUser = authenticatedUser.get();
    if (maybeUser.isPresent()) {
      this.user = maybeUser.get();

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
      if (showSettings) {
        this.settingsDialog = this.createSettingsDialog(user);
        userName.getSubMenu().addItem("Settings", e -> this.settingsDialog.open());
      }

      this.add(userMenu);
    } else {
      Anchor loginLink = new Anchor("login", "Sign in");
      this.add(loginLink);
    }
  }

  private Dialog createSettingsDialog(User user) {
    Dialog dialog = new Dialog();

    dialog.add(new H3("Settings"));

    Div editorDiv = new Div();
    dialog.add(editorDiv);

    FormLayout formLayout = new FormLayout();

    TextField username = new TextField("Username");
    username.setReadOnly(true);

    TextField name = new TextField("Name");
    PasswordField password = new PasswordField("Password");

    formLayout.add(username, name, password);
    editorDiv.add(formLayout);

    BeanValidationBinder<User> binder = new BeanValidationBinder<>(User.class);
    binder.bind(username, "username");
    binder.bind(name, "name");
    binder.bind(password, u -> "********",
        (u, p) -> u.setHashedPassword(this.securityConfiguration.passwordEncoder().encode(p)));

    binder.readBean(this.user);

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setClassName("button-layout");

    Button save = new Button("Save");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      try {
        binder.writeBean(this.user);
        if (user.getProfilePicture() == null) {
          user.setProfilePicture(new byte[]{});
        }
        userService.update(this.user);
        dialog.close();
      } catch (ObjectOptimisticLockingFailureException exception) {
        Notification n = Notification.show("Error updating the data.");
        n.setPosition(Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      } catch (ValidationException validationException) {
        Notification.show("Failed to update the data. Check again that all values are valid");
      }
    });

    Button cancel = new Button("Cancel");
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    cancel.addClickListener(e -> dialog.close());

    buttonLayout.add(save, cancel);
    editorDiv.add(buttonLayout);

    return dialog;
  }
}
