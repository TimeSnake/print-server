/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.views.user;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.provider.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.timesnake.web.data.Role;
import de.timesnake.web.data.entity.User;
import de.timesnake.web.data.service.UserService;
import de.timesnake.web.security.SecurityConfiguration;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@PageTitle("Users")
@Route(value = "users/:userID?")
@RolesAllowed("ADMIN")
public class UserView extends Div implements BeforeEnterObserver {

  private final Grid<User> grid = new Grid<>(User.class, false);

  private final ConsumerDataProvider dataProvider;
  private final UserFilter filter = new UserFilter();
  private final ConfigurableFilterDataProvider<User, Void, UserFilter> filterDataProvider;

  private final Dialog userPopup = new Dialog();

  private BeanValidationBinder<User> binder;
  private final MultiSelectListBox<Role> roleList = new MultiSelectListBox<>();

  private User user;

  private final UserService userService;
  private final SecurityConfiguration securityConfiguration;

  public UserView(UserService userService, SecurityConfiguration securityConfiguration) {
    this.userService = userService;
    this.securityConfiguration = securityConfiguration;
    this.dataProvider = new ConsumerDataProvider(userService);
    this.filterDataProvider = dataProvider.withConfigurableFilter();

    this.createConsumerPopupDialog();

    HorizontalLayout horizontalLayout = new HorizontalLayout();
    add(horizontalLayout);

    TextField searchField = new TextField();
    searchField.setWidth("10rem");
    searchField.getStyle().setMargin("0 0 0 1rem");
    searchField.setPlaceholder("Search");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> {
      filter.setSearchTerm(e.getValue());
      filterDataProvider.setFilter(filter);
    });
    horizontalLayout.add(searchField);

    Button addButton = new Button("Add");
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> this.openPopupDialog(null));
    horizontalLayout.add(addButton);

    add(grid);

    grid.addColumn("username").setAutoWidth(true).setFlexGrow(0);
    grid.addColumn("name").setAutoWidth(true).setFlexGrow(0);

    grid.setItems(this.filterDataProvider);
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_NO_ROW_BORDERS, GridVariant.LUMO_ROW_STRIPES);
    grid.setMinWidth("50%");

    grid.asSingleSelect().addValueChangeListener(event -> {
      if (event.getValue() != null) {
        UI.getCurrent().navigate(String.format("users/%s", event.getValue().getId()));
      } else {
        clearForm();
        UI.getCurrent().navigate(UserView.class);
      }
    });
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    Optional<Long> userId = event.getRouteParameters().get("userID").map(Long::parseLong);
    if (userId.isPresent()) {
      Optional<User> userFromBackend = userService.get(userId.get());
      if (userFromBackend.isPresent()) {
        openPopupDialog(userFromBackend.get());
      } else {
        Notification.show(String.format("The requested user was not found, ID = %s", userId.get()),
            3000, Position.BOTTOM_START);
        refreshGrid();
        event.forwardTo(UserView.class);
      }
    }
  }

  private void createConsumerPopupDialog() {
    this.userPopup.add(new H3("User"));

    Div editorDiv = new Div();
    this.userPopup.add(editorDiv);

    FormLayout formLayout = new FormLayout();

    TextField username = new TextField("Username");
    TextField name = new TextField("Name");
    PasswordField password = new PasswordField("Password");

    roleList.getStyle().setMargin("1rem 0 0 0");
    roleList.setTooltipText("Roles");

    formLayout.add(username, name, password, roleList);
    editorDiv.add(formLayout);

    binder = new BeanValidationBinder<>(User.class);
    binder.bind(username, "username");
    binder.bind(name, "name");
    binder.bind(password, u -> "********",
        (u, p) -> u.setHashedPassword(this.securityConfiguration.passwordEncoder().encode(p)));

    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setClassName("button-layout");

    Button save = new Button("Save");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      try {
        if (this.user == null) {
          this.user = new User();
        }
        binder.writeBean(this.user);
        this.user.setRoles(roleList.getSelectedItems());
        if (user.getProfilePicture() == null) {
          user.setProfilePicture(new byte[]{});
        }
        userService.update(this.user);
        clearForm();
        refreshGrid();
        this.userPopup.close();
        Notification.show("Data updated");
        UI.getCurrent().navigate(UserView.class);
      } catch (ObjectOptimisticLockingFailureException exception) {
        Notification n = Notification.show(
            "Error updating the data. Somebody else has updated the record while you were making changes.");
        n.setPosition(Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      } catch (ValidationException validationException) {
        Notification.show("Failed to update the data. Check again that all values are valid");
      }
    });


    Button cancel = new Button("Cancel");
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    cancel.addClickListener(e -> {
      clearForm();
      refreshGrid();
      this.userPopup.close();
    });

    //Button delete = new Button("Delete");
    //delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
    //delete.addClickListener(e -> {
    //  try {
    //    if (this.user == null) {
    //      return;
    //    }
    //    userService.delete(this.user.getId());
    //    clearForm();
    //    refreshGrid();
    //    this.userPopup.close();
    //    Notification.show("Data updated");
    //    UI.getCurrent().navigate(UserView.class);
    //  } catch (ObjectOptimisticLockingFailureException exception) {
    //    Notification n = Notification.show(
    //        "Error updating the data. Somebody else has updated the record while you were making changes.");
    //    n.setPosition(Position.MIDDLE);
    //    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    //  }
    //});

    buttonLayout.add(save, cancel);
    //buttonLayout.add(delete);
    editorDiv.add(buttonLayout);
  }

  private void refreshGrid() {
    grid.select(null);
    grid.getDataProvider().refreshAll();
  }

  private void clearForm() {
    openPopupDialog(null);
  }

  private void openPopupDialog(User value) {
    this.user = value;
    binder.readBean(this.user);
    roleList.setItems(Role.values());
    if (this.user != null) {
      roleList.select(this.user.getRoles());
    }

    this.userPopup.open();
  }

  public static class ConsumerDataProvider extends AbstractBackEndDataProvider<User, UserFilter> {

    private final UserService userService;

    public ConsumerDataProvider(UserService userService) {
      this.userService = userService;
    }

    @Override
    protected Stream<User> fetchFromBackEnd(Query<User, UserFilter> query) {
      Stream<User> stream = userService.list(PageRequest.of(query.getPage(), query.getPageSize(),
          VaadinSpringDataHelpers.toSpringDataSort(query))).stream();

      // Filtering
      if (query.getFilter().isPresent()) {
        stream = stream.filter(cmd -> query.getFilter().get().test(cmd));
      }

      // Sorting
      if (!query.getSortOrders().isEmpty()) {
        stream = stream.sorted(sortComparator(query.getSortOrders()));
      }

      return stream;
    }

    @Override
    protected int sizeInBackEnd(Query<User, UserFilter> query) {
      return (int) fetchFromBackEnd(query).count();
    }

    private static Comparator<User> sortComparator(List<QuerySortOrder> sortOrders) {
      return sortOrders.stream().map(sortOrder -> {
        Comparator<User> comparator = fieldComparator(sortOrder.getSorted());

        if (sortOrder.getDirection() == SortDirection.DESCENDING) {
          comparator = comparator.reversed();
        }

        return comparator;
      }).reduce(Comparator::thenComparing).orElse((p1, p2) -> 0);
    }

    private static Comparator<User> fieldComparator(String sorted) {
      if (sorted.equals("username")) {
        return Comparator.comparing(User::getUsername);
      } else if (sorted.equals("name")) {
        return Comparator.comparing(User::getName);
      }
      return (p1, p2) -> 0;
    }
  }

  public static class UserFilter {

    private String searchTerm;

    public void setSearchTerm(String searchTerm) {
      this.searchTerm = searchTerm;
    }

    public boolean test(User cmd) {
      return matches(cmd.getName(), searchTerm);
    }

    private boolean matches(String value, String searchTerm) {
      return searchTerm == null || searchTerm.isEmpty()
          || value.toLowerCase().contains(searchTerm.toLowerCase());
    }
  }
}
