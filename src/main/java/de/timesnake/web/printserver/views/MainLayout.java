package de.timesnake.web.printserver.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import de.timesnake.web.printserver.security.AuthenticatedUser;
import de.timesnake.web.printserver.views.print.PrintView;
import de.timesnake.web.printserver.views.printer.PrintersView;
import de.timesnake.web.printserver.views.user.UserMenu;
import de.timesnake.web.printserver.views.user.UserView;

public class MainLayout extends AppLayout {

  private final AuthenticatedUser authenticatedUser;

  public MainLayout(AuthenticatedUser authenticatedUser) {
    this.authenticatedUser = authenticatedUser;

    DrawerToggle toggle = new DrawerToggle();

    H1 title = new H1("Print-Server");
    title.getStyle().set("font-size", "var(--lumo-font-size-l)")
        .set("margin", "0");

    addToDrawer(new Scroller(createTabs()));
    addToDrawer(this.createFooter());
    addToNavbar(toggle, title);
  }

  private Tabs createTabs() {
    Tabs tabs = new Tabs();
    tabs.add(createTab(VaadinIcon.PRINT, "Print", PrintView.class),
        createTab(VaadinIcon.USERS, "Users", UserView.class),
        createTab(VaadinIcon.HARDDRIVE, "Printers", PrintersView.class));
    tabs.setOrientation(Tabs.Orientation.VERTICAL);
    return tabs;
  }

  private Tab createTab(VaadinIcon viewIcon, String viewName, Class<? extends Component> view) {
    Icon icon = viewIcon.create();
    icon.getStyle().set("box-sizing", "border-box")
        .set("margin-inline-end", "var(--lumo-space-m)")
        .set("padding", "var(--lumo-space-xs)");

    RouterLink link = new RouterLink();
    link.add(icon, new Span(viewName));
    link.setRoute(view);
    link.setTabIndex(1);

    return new Tab(link);
  }

  private Footer createFooter() {
    Footer layout = new Footer();

    layout.add(new UserMenu(this.authenticatedUser));

    return layout;
  }
}
