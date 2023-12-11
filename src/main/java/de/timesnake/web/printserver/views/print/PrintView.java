package de.timesnake.web.printserver.views.print;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileData;
import com.vaadin.flow.component.upload.receivers.MultiFileBuffer;
import com.vaadin.flow.data.provider.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.data.service.PrintJobRepository;
import de.timesnake.web.printserver.security.AuthenticatedUser;
import de.timesnake.web.printserver.util.PrintListener;
import de.timesnake.web.printserver.util.PrintRequest;
import de.timesnake.web.printserver.util.PrintResult;
import de.timesnake.web.printserver.util.PrintService;
import de.timesnake.web.printserver.views.MainLayout;
import elemental.json.JsonValue;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@RolesAllowed(value = {"USER"})
@PageTitle("Print")
@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "print", layout = MainLayout.class)
public class PrintView extends Div {

  private final PrintService printService;
  private final User user;

  private final FlexLayout main;

  private Upload upload;

  private final VerticalLayout print;
  private VerticalLayout uploadSection;
  private VerticalLayout printOptionsSection;
  private VerticalLayout printSection;

  private Button printButton;

  private final VerticalLayout log;

  private Select<Printer> printerSelect;
  private RadioButtonGroup<PrintRequest.PrintOrientation> orientationRadio;
  private RadioButtonGroup<PrintRequest.PrintSides> sidesRadio;
  private Select<PrintRequest.PrintPerPage> perPageSelect;
  private TextField pageRange;
  private IntegerField quantity;

  private final Grid<PrintRequest> processingGrid = new Grid<>(PrintRequest.class, false);
  private final List<PrintRequest> requests = new LinkedList<>();

  private final Grid<PrintJob> logGrid = new Grid<>(PrintJob.class, false);

  private MultiFileBuffer fileBuffer;

  private List<String> uploadedFileNames = new LinkedList<>();

  public PrintView(PrintService printService, AuthenticatedUser user) {
    this.printService = printService;
    this.user = user.get().get();

    this.main = new FlexLayout();
    this.add(this.main);

    this.main.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    this.main.getStyle().set("gap", "2rem");
    this.main.setWidth(100, Unit.PERCENTAGE);

    this.print = new VerticalLayout();
    this.main.add(this.print);

    this.print.setWidth(30, Unit.REM);

    this.createUploadSection();
    this.createPrintOptionsSection();
    this.createPrintSection();

    this.log = new VerticalLayout();
    this.main.add(this.log);

    this.log.setWidth(42, Unit.REM);

    this.createProcessingGrid();
    this.createLogGrid();
  }

  private void createUploadSection() {
    this.uploadSection = new VerticalLayout();
    this.print.add(this.uploadSection);

    this.uploadSection.add(new H3("Files"));

    this.fileBuffer = new MultiFileBuffer();

    this.upload = new Upload(this.fileBuffer);
    this.upload.setDropAllowed(true);
    this.upload.setAcceptedFileTypes("application/pdf", ".pdf", "image/jpeg", ".jpg", ".jpeg");
    this.upload.setMaxFileSize(100 * 1024 * 1024);
    this.upload.setMaxFiles(5);

    this.upload.getElement().executeJs("this.addEventListener('file-remove', " +
        "(e) => $0.$server.fileRemove(e.detail.file.name));", getElement());

    this.upload.addSucceededListener(e -> {
      PrintView.this.uploadedFileNames.add(e.getFileName());
    });

    this.upload.addFileRejectedListener(e -> {
      String errorMsg = e.getErrorMessage();
      Notification notification = Notification.show(errorMsg, 5000, Notification.Position.TOP_CENTER);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    });

    this.uploadSection.add(this.upload);
  }

  @ClientCallable
  public void fileRemove(JsonValue event) {
    this.uploadedFileNames.removeIf(e -> event.toJson().equals("\"" + e + "\""));
  }

  private void createPrintOptionsSection() {
    this.printOptionsSection = new VerticalLayout();
    this.print.add(this.printOptionsSection);

    this.printOptionsSection.add(new H3("Options"));

    this.printerSelect = new Select<>();
    this.printerSelect.setLabel("Printer");
    this.printerSelect.setItems(this.printService.getPrinterRepository().findAll());
    this.printerSelect.setItemLabelGenerator(Printer::getName);
    this.printerSelect.setPlaceholder(this.printService.getDefaultPrinter().getName());
    this.printerSelect.setValue(this.printService.getDefaultPrinter());
    this.printerSelect.setWidth(15, Unit.REM);
    this.printOptionsSection.add(this.printerSelect);

    FlexLayout h1 = new FlexLayout();
    this.printOptionsSection.add(h1);
    h1.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    h1.getStyle().set("gap", "1rem");

    this.orientationRadio = new RadioButtonGroup<>();
    this.orientationRadio.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
    this.orientationRadio.setLabel("Orientation");
    this.orientationRadio.setItems(PrintRequest.PrintOrientation.values());
    this.orientationRadio.setValue(PrintRequest.PrintOrientation.PORTRAIT);
    h1.add(this.orientationRadio);

    this.sidesRadio = new RadioButtonGroup<>();
    this.sidesRadio.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
    this.sidesRadio.setLabel("Sides");
    this.sidesRadio.setItems(PrintRequest.PrintSides.values());
    this.sidesRadio.setValue(PrintRequest.PrintSides.ONE_SIDED);
    h1.add(this.sidesRadio);

    FlexLayout h2 = new FlexLayout();
    this.printOptionsSection.add(h2);
    h2.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    h2.getStyle().set("gap", "1rem");

    this.perPageSelect = new Select<>();
    this.perPageSelect.setLabel("Per page");
    this.perPageSelect.setItems(PrintRequest.PrintPerPage.values());
    this.perPageSelect.setValue(PrintRequest.PrintPerPage.ONE);
    this.perPageSelect.setWidth(6, Unit.REM);
    h2.add(this.perPageSelect);

    this.quantity = new IntegerField();
    this.quantity.setLabel("Quantity");
    this.quantity.setHelperText("Max 10 items");
    this.quantity.setMin(1);
    this.quantity.setMax(10);
    this.quantity.setValue(1);
    this.quantity.setWidth(7, Unit.REM);
    this.quantity.setStepButtonsVisible(true);
    h2.add(quantity);

    this.pageRange = new TextField();
    this.pageRange.setLabel("Page Range");
    this.pageRange.setPattern("[0-9]+(-[0-9]+)?(,[0-9]+(-[0-9]+)?)*");
    this.pageRange.setAllowedCharPattern("[0-9-,]");
    this.pageRange.setHelperText("ex. 1,4-9");
    this.pageRange.setWidth(10, Unit.REM);
    h2.add(this.pageRange);
  }

  private void createPrintSection() {
    this.printSection = new VerticalLayout();
    this.print.add(this.printSection);

    this.printButton = new Button("Print");
    this.printSection.add(this.printButton);

    this.printButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    this.printButton.setDisableOnClick(true);
    this.printButton.setIcon(VaadinIcon.PRINT.create());

    this.printButton.addClickListener(e -> {
      this.printFiles();
    });
  }

  private void printFiles() {
    this.requests.clear();

    for (String name : this.fileBuffer.getFiles()) {
      FileData fileData = this.fileBuffer.getFileData(name);

      if (!this.uploadedFileNames.contains(fileData.getFileName())) {
        continue;
      }

      requests.add(printService.createRequest(fileData.getFile())
          .user(this.user)
          .name(fileData.getFileName())
          .printer(this.printerSelect.getValue())
          .orientation(this.orientationRadio.getValue())
          .sides(this.sidesRadio.getValue())
          .perPage(this.perPageSelect.getValue())
          .range(PrintRequest.PageRange.fromString(this.pageRange.getValue()))
          .copies(this.quantity.getValue()));
    }

    this.fileBuffer = new MultiFileBuffer();
    this.upload.clearFileList();
    this.upload.setReceiver(this.fileBuffer);

    this.processingGrid.getDataProvider().refreshAll();
    this.logGrid.getDataProvider().refreshAll();

    this.printService.getExecutorService().execute(() -> {
      try {
        this.printService.process(requests, new PrintListener() {
          @Override
          public void onPrinting(PrintRequest request) {
            PrintView.this.getUI().ifPresent(ui -> ui.access(() -> {
              PrintView.this.processingGrid.getDataProvider().refreshItem(request);
              PrintView.this.getUI().get().push();
            }));
          }

          @Override
          public void onCompleted(PrintRequest request, PrintResult result) {
            PrintView.this.getUI().ifPresent(ui -> ui.access(() -> {
              PrintView.this.processingGrid.getDataProvider().refreshItem(request);
              PrintView.this.logGrid.getDataProvider().refreshItem(request.getJob());
              PrintView.this.getUI().get().push();
            }));
          }

          @Override
          public void onError(PrintResult result) {
            PrintView.this.getUI().ifPresent(ui -> ui.access(() -> {
              PrintView.this.processingGrid.getDataProvider().refreshItem(result.getRequest());
              PrintView.this.getUI().get().push();
            }));
          }
        }).get();
      } catch (InterruptedException | ExecutionException ex) {
        Application.getLogger().warning("Exception while waiting for request from user '" + this.user.getUsername() + "': " + ex.getMessage());
      }

      this.printButton.setEnabled(true);
    });
  }

  public void createProcessingGrid() {
    this.log.add(new H3("Jobs"));
    this.log.add(this.processingGrid);

    this.processingGrid.addColumn("name")
        .setHeader("File Name")
        .setWidth("20rem")
        .setFlexGrow(0);
    this.processingGrid.addComponentColumn(r -> {
          Span badge = new Span();
          switch (r.getStatus()) {
            case CREATED, QUEUED -> {
              Icon icon = VaadinIcon.HAND.create();
              icon.getStyle().set("padding", "var(--lumo-space-xs");
              badge.add(icon);
              badge.add(new Span("Queued"));
              badge.getElement().getThemeList().add("badge contrast");
            }
            case PRINTING -> {
              Icon icon = VaadinIcon.CLOCK.create();
              icon.getStyle().set("padding", "var(--lumo-space-xs");
              badge.add(icon);
              badge.add(new Span("Printing"));
              badge.getElement().getThemeList().add("badge");
            }
            case COMPLETED -> {
              Icon icon = VaadinIcon.CHECK.create();
              icon.getStyle().set("padding", "var(--lumo-space-xs");
              badge.add(icon);
              badge.add(new Span("Completed (" + new DecimalFormat("0.00").format(r.getJob().getCosts()) + " €)"));
              badge.getElement().getThemeList().add("badge success");
            }
            case ERROR -> {
              Icon icon = VaadinIcon.EXCLAMATION_CIRCLE_O.create();
              icon.getStyle().set("padding", "var(--lumo-space-xs");
              badge.add(icon);
              badge.add(new Span("Error (" + r.getResult().getErrorType().getUserMessage() + ")"));
              badge.getElement().getThemeList().add("badge error");
            }
          }
          return badge;
        })
        .setHeader("Status")
        .setWidth("12rem")
        .setFlexGrow(0);

    this.processingGrid.setItems(this.requests);

    this.processingGrid.setWidth(32, Unit.REM);
    this.processingGrid.setAllRowsVisible(true);
    this.processingGrid.setMinHeight(2, Unit.REM);
    this.processingGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
  }

  public void createLogGrid() {
    this.log.add(new H3("Log"));
    this.log.add(this.logGrid);

    this.logGrid.addColumn("fileName")
        .setHeader("File Name")
        .setWidth("20rem")
        .setFlexGrow(0);
    this.logGrid.addColumn(j -> DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss")
            .format(j.getTimestamp().atZone(ZoneId.systemDefault())), "timestamp")
        .setSortable(true)
        .setHeader("Date")
        .setAutoWidth(true)
        .setFlexGrow(0);
    this.logGrid.addColumn("printedPages")
        .setHeader("Pages")
        .setAutoWidth(true)
        .setSortable(false)
        .setFlexGrow(0);
    this.logGrid.addColumn(j -> new DecimalFormat("0.00").format(j.getCosts()) + " €")
        .setHeader("Costs")
        .setAutoWidth(true)
        .setSortable(true)
        .setFlexGrow(0);

    this.logGrid.setWidth(40, Unit.REM);
    this.logGrid.setMaxWidth(100, Unit.PERCENTAGE);
    this.logGrid.setMinHeight(40, Unit.REM);
    this.logGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

    PrintJobDataProvider dataProvider = new PrintJobDataProvider(this.printService.getPrintJobRepository());
    ConfigurableFilterDataProvider<PrintJob, Void, PrintJobFilter> filterDataProvider =
        dataProvider.withConfigurableFilter();
    this.logGrid.setItems(filterDataProvider);
  }

  public static class PrintJobDataProvider extends AbstractBackEndDataProvider<PrintJob, PrintJobFilter> {

    private final PrintJobRepository printerRepository;

    public PrintJobDataProvider(PrintJobRepository printerRepository) {
      this.printerRepository = printerRepository;
    }

    @Override
    protected Stream<PrintJob> fetchFromBackEnd(Query<PrintJob, PrintJobFilter> query) {
      Stream<PrintJob> stream = printerRepository.findAll(PageRequest.of(query.getPage(), query.getPageSize(),
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
