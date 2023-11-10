package de.timesnake.web.printserver.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import de.timesnake.web.printserver.views.print.PrintView;
import de.timesnake.web.printserver.views.printer.PrinterView;
import de.timesnake.web.printserver.views.user.UserView;

public class MainLayout extends AppLayout {

  public MainLayout() {
    DrawerToggle toggle = new DrawerToggle();

    H1 title = new H1("Dashboard");
    title.getStyle().set("font-size", "var(--lumo-font-size-l)")
        .set("margin", "0");

    Tabs tabs = getTabs();

    addToDrawer(tabs);
    addToNavbar(toggle, title);
  }

  private Tabs getTabs() {
    Tabs tabs = new Tabs();
    tabs.add(createTab(VaadinIcon.PRINT, "Print", PrintView.class),
        createTab(VaadinIcon.USERS, "Users", UserView.class),
        createTab(VaadinIcon.HARDDRIVE, "Printer", PrinterView.class));
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
}
