package de.timesnake.web.printserver.views.printer;

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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.converter.StringToDoubleConverter;
import com.vaadin.flow.data.converter.StringToIntegerConverter;
import com.vaadin.flow.data.provider.*;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.PrinterRepository;
import de.timesnake.web.printserver.util.PrintService;
import de.timesnake.web.printserver.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@RolesAllowed(value = {"ADMIN"})
@Route(value = "printer/:printerID?", layout = MainLayout.class)
public class PrintersView extends Div implements BeforeEnterObserver {

  private final String PRINTER_EDIT_ROUTE_TEMPLATE = "printer/%s";

  private final Grid<Printer> grid = new Grid<>(Printer.class, false);

  private final Dialog popup = new Dialog();

  private BeanValidationBinder<Printer> binder;

  private final PrinterRepository printerRepository;

  private final PrinterDataProvider dataProvider;
  private final PrinterFilter printerFilter = new PrinterFilter();
  private final ConfigurableFilterDataProvider<Printer, Void, PrinterFilter> filterDataProvider;

  private final PrinterView printerView;

  private Printer printer;

  public PrintersView(PrintService printService) {
    this.printerRepository = printService.getPrinterRepository();

    this.printerView = new PrinterView(printService);

    this.dataProvider = new PrinterDataProvider(printerRepository);
    this.filterDataProvider = dataProvider.withConfigurableFilter();

    this.createPopupDialog();

    HorizontalLayout main = new HorizontalLayout();
    this.add(main);

    VerticalLayout printers = new VerticalLayout();
    main.add(printers);

    HorizontalLayout horizontalLayout = new HorizontalLayout();
    printers.add(horizontalLayout);

    TextField searchField = new TextField();
    searchField.setWidth("10rem");
    searchField.getStyle().setMargin("0 0 0 1rem");
    searchField.setPlaceholder("Search");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> {
      printerFilter.setSearchTerm(e.getValue());
      filterDataProvider.setFilter(printerFilter);
    });
    horizontalLayout.add(searchField);

    Button addButton = new Button("Add");
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    addButton.addClickListener(e -> this.openPopupDialog(null));
    horizontalLayout.add(addButton);

    printers.add(grid);

    grid.addColumn("name")
        .setHeader("Name")
        .setAutoWidth(true)
        .setSortable(true)
        .setFlexGrow(0);
    grid.addColumn("cupsName")
        .setHeader("CUPS Name")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("priceOneSided")
        .setHeader("Price One-Sided")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("priceTwoSided")
        .setHeader("Price Two-Sided")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("priority")
        .setHeader("Priority")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn(
            new ComponentRenderer<>(Button::new, (button, printer) -> {
              button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
              button.addClickListener(e -> {
                UI.getCurrent().navigate(String.format(PRINTER_EDIT_ROUTE_TEMPLATE, printer.getId()));
                this.printerView.setPrinter(printer);
              });
              button.setIcon(new Icon(VaadinIcon.DATABASE));
            }))
        .setFlexGrow(0)
        .setAutoWidth(true);
    grid.setItems(this.filterDataProvider);
    grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT, GridVariant.LUMO_NO_BORDER,
        GridVariant.LUMO_NO_ROW_BORDERS, GridVariant.LUMO_ROW_STRIPES);
    grid.setMinWidth("50%");

    grid.asSingleSelect().addValueChangeListener(event -> {
      if (event.getValue() != null) {
        populateForm(event.getValue());
      } else {
        clearForm();
        UI.getCurrent().navigate(PrintersView.class);
      }
    });

    main.add(this.printerView);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    Optional<Long> printerId = event.getRouteParameters().get("printerID").map(Long::parseLong);
    if (printerId.isPresent()) {
      Optional<Printer> printerFromBackend = this.printerRepository.findById(printerId.get());
      printerFromBackend.ifPresent(this.printerView::setPrinter);
    }
  }

  private void populateForm(Printer value) {
    this.printer = value;
    binder.readBean(this.printer);
    this.popup.open();
  }

  private void createPopupDialog() {
    this.popup.add(new H3("Drink Type"));

    Div editorDiv = new Div();
    this.popup.add(editorDiv);

    FormLayout formLayout = new FormLayout();

    TextField name = new TextField("Name");
    TextField cupsName = new TextField("CUPS Name");

    TextField priceOneSided = new TextField("Price Per One-Sided Page");
    priceOneSided.setPattern("[+-]?[0-9]*[\\.]?[0-9]*");
    TextField priceTwoSided = new TextField("Price Per Two-Sided Page");
    priceTwoSided.setPattern("[+-]?[0-9]*[\\.]?[0-9]*");
    TextField priority = new TextField("Priority");
    priority.setPattern("[0-9]+");

    formLayout.add(name, cupsName, priceOneSided, priceTwoSided, priority);
    editorDiv.add(formLayout);

    binder = new BeanValidationBinder<>(Printer.class);
    binder.bind(name, "name");
    binder.bind(cupsName, "cupsName");
    binder.forField(priceOneSided).withConverter(new StringToDoubleConverter("Only floating point numbers are " +
        "allowed")).bind("priceOneSided");
    binder.forField(priceTwoSided).withConverter(new StringToDoubleConverter("Only floating point numbers are " +
        "allowed")).bind("priceTwoSided");
    binder.forField(priority).withConverter(new StringToIntegerConverter("Only floating point numbers are " +
        "allowed")).bind("priority");
    binder.bindInstanceFields(this);

    HorizontalLayout buttonLayout = new HorizontalLayout();

    Button cancel = new Button("Cancel");
    cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    cancel.addClickListener(e -> {
      clearForm();
      refreshGrid();
      this.popup.close();
    });

    Button save = new Button("Save");
    save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    save.addClickListener(e -> {
      try {
        if (this.printer == null) {
          this.printer = new Printer();
        }
        binder.writeBean(this.printer);
        printerRepository.save(this.printer);
        clearForm();
        refreshGrid();
        this.popup.close();
        Notification.show("Data updated");
        UI.getCurrent().navigate(PrintersView.class);
      } catch (ObjectOptimisticLockingFailureException exception) {
        Notification n = Notification.show("Error updating the data. Somebody else has updated the record while you " +
            "were making changes.");
        n.setPosition(Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      } catch (ValidationException validationException) {
        Notification.show("Failed to update the data. Check again that all values are valid");
      }
    });

    buttonLayout.add(save, cancel);
    editorDiv.add(buttonLayout);
  }

  private void refreshGrid() {
    grid.select(null);
    grid.getDataProvider().refreshAll();
  }

  private void clearForm() {
    openPopupDialog(null);
  }

  private void openPopupDialog(Printer value) {
    this.printer = value;
    binder.readBean(this.printer);

    this.popup.open();
  }

  public static class PrinterDataProvider extends AbstractBackEndDataProvider<Printer, PrinterFilter> {

    private final PrinterRepository printerRepository;

    public PrinterDataProvider(PrinterRepository printerRepository) {
      this.printerRepository = printerRepository;
    }

    @Override
    protected Stream<Printer> fetchFromBackEnd(Query<Printer, PrinterFilter> query) {
      Stream<Printer> stream = printerRepository.findAll(PageRequest.of(query.getPage(), query.getPageSize(),
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
    protected int sizeInBackEnd(Query<Printer, PrinterFilter> query) {
      return (int) fetchFromBackEnd(query).count();
    }

    private static Comparator<Printer> sortComparator(List<QuerySortOrder> sortOrders) {
      return sortOrders.stream().map(sortOrder -> {
        Comparator<Printer> comparator = fieldComparator(sortOrder.getSorted());

        if (sortOrder.getDirection() == SortDirection.DESCENDING) {
          comparator = comparator.reversed();
        }

        return comparator;
      }).reduce(Comparator::thenComparing).orElse((p1, p2) -> 0);
    }

    private static Comparator<Printer> fieldComparator(String sorted) {
      return switch (sorted) {
        case "id" -> Comparator.comparing(Printer::getId);
        case "cupsName" -> Comparator.comparing(Printer::getCupsName);
        case "name" -> Comparator.comparing(Printer::getName);
        default -> (p1, p2) -> 0;
      };
    }
  }

  public static class PrinterFilter {

    private String searchTerm;

    public void setSearchTerm(String searchTerm) {
      this.searchTerm = searchTerm;
    }

    public boolean test(Printer cmd) {
      return matches(cmd.getName(), searchTerm);
    }

    private boolean matches(String value, String searchTerm) {
      return searchTerm == null || searchTerm.isEmpty()
          || value.toLowerCase().contains(searchTerm.toLowerCase());
    }
  }
}
