/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver.views.printer;

import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.service.PrintJobRepository;
import de.timesnake.web.printserver.util.PrintService;
import org.springframework.data.domain.PageRequest;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class PrinterView extends VerticalLayout {

  private final Grid<PrintJob> grid = new Grid<>(PrintJob.class, false);

  private final PrintJobFilter printJobFilter;
  private final PrintJobDataProvider dataProvider;
  private final ConfigurableFilterDataProvider<PrintJob, Void, PrintJobFilter> filterDataProvider;

  private final H3 title;

  Printer printer;

  private final PrintService printService;

  public PrinterView(PrintService printService) {
    this.printService = printService;

    this.printJobFilter = new PrintJobFilter();
    this.dataProvider = new PrintJobDataProvider(this.printService.getPrintJobRepository());
    this.filterDataProvider = this.dataProvider.withConfigurableFilter();

    this.title = new H3();
    add(title);

    HorizontalLayout head = new HorizontalLayout();
    add(head);

    TextField searchField = new TextField();
    searchField.setWidth("10rem");
    searchField.setPlaceholder("Search");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> {
      printJobFilter.setSearchTerm(e.getValue());
      filterDataProvider.setFilter(printJobFilter);
    });
    head.add(searchField);

    add(grid);

    grid.addColumn("fileName")
        .setHeader("File Name")
        .setWidth("20rem")
        .setFlexGrow(0);
    grid.addColumn(j -> j.getUser().getUsername())
        .setHeader("User")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn(j -> DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss")
            .format(j.getTimestamp().atZone(ZoneId.systemDefault())), "timestamp")
        .setSortable(true)
        .setHeader("Date")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("documentPages")
        .setHeader("Document")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("selectedPages")
        .setHeader("Selected")
        .setAutoWidth(true)
        .setFlexGrow(0);
    grid.addColumn("printedPages")
        .setHeader("Printed")
        .setAutoWidth(true)
        .setFlexGrow(0);

    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
    grid.setAllRowsVisible(true);
    grid.setMaxHeight(100, Unit.PERCENTAGE);


    grid.setItems(filterDataProvider);
    grid.recalculateColumnWidths();
    this.refreshGrid();
  }

  public void setPrinter(Printer printer) {
    this.title.setText(printer.getName());
    this.printer = printer;
    this.refreshGrid();
  }

  void refreshGrid() {
    grid.getDataProvider().refreshAll();
    grid.recalculateColumnWidths();
  }


  public class PrintJobDataProvider extends AbstractBackEndDataProvider<PrintJob, PrintJobFilter> {

    private final PrintJobRepository printerRepository;

    public PrintJobDataProvider(PrintJobRepository printerRepository) {
      this.printerRepository = printerRepository;
    }

    @Override
    protected Stream<PrintJob> fetchFromBackEnd(Query<PrintJob, PrintJobFilter> query) {
      Stream<PrintJob> stream = printerRepository.getPrintJobsByPrinter(PageRequest.of(query.getPage(), query.getPageSize(),
              VaadinSpringDataHelpers.toSpringDataSort(query)), PrinterView.this.printer).stream();

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
    protected int sizeInBackEnd(Query<PrintJob, PrintJobFilter> query) {
      return (int) fetchFromBackEnd(query).count();
    }

    private static Comparator<PrintJob> sortComparator(List<QuerySortOrder> sortOrders) {
      return sortOrders.stream().map(sortOrder -> {
        Comparator<PrintJob> comparator = fieldComparator(sortOrder.getSorted());

        if (sortOrder.getDirection() == SortDirection.DESCENDING) {
          comparator = comparator.reversed();
        }

        return comparator;
      }).reduce(Comparator::thenComparing).orElse((p1, p2) -> 0);
    }

    private static Comparator<PrintJob> fieldComparator(String sorted) {
      return switch (sorted) {
        case "timestamp" -> Comparator.comparing(PrintJob::getTimestamp);
        case "fileName" -> Comparator.comparing(PrintJob::getFileName);
        default -> Comparator.comparing(PrintJob::getTimestamp).reversed();
      };
    }
  }

  public static class PrintJobFilter {

    private String searchTerm;

    public void setSearchTerm(String searchTerm) {
      this.searchTerm = searchTerm;
    }

    public boolean test(PrintJob cmd) {
      return matches(cmd.getFileName(), searchTerm);
    }

    private boolean matches(String value, String searchTerm) {
      return searchTerm == null || searchTerm.isEmpty()
          || value.toLowerCase().contains(searchTerm.toLowerCase());
    }
  }
}
