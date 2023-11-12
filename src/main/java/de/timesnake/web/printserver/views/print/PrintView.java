package de.timesnake.web.printserver.views.print;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileData;
import com.vaadin.flow.component.upload.receivers.MultiFileBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.security.AuthenticatedUser;
import de.timesnake.web.printserver.util.PrintListener;
import de.timesnake.web.printserver.util.PrintRequest;
import de.timesnake.web.printserver.util.PrintResult;
import de.timesnake.web.printserver.util.PrintService;
import de.timesnake.web.printserver.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

@RolesAllowed(value = {"USER"})
@PageTitle("Print")
@Route(value = "print", layout = MainLayout.class)
public class PrintView extends Div {

  private final PrintService printService;
  private final User user;

  private final HorizontalLayout main;

  private final VerticalLayout print;
  private VerticalLayout uploadSection;
  private VerticalLayout printOptionsSection;
  private VerticalLayout printSection;

  private final VerticalLayout log;

  private RadioButtonGroup<PrintRequest.PrintOrientation> orientationRadio;
  private RadioButtonGroup<PrintRequest.PrintSides> sidesRadio;
  private Select<PrintRequest.PrintPerPage> perPageSelect;
  private TextField pageRange;
  private IntegerField quantity;

  private Grid<PrintRequest> processingGrid = new Grid<>();
  private Grid<PrintJob> logGrid = new Grid<>();

  private MultiFileBuffer fileBuffer;

  public PrintView(PrintService printService, AuthenticatedUser user) {
    this.printService = printService;
    this.user = user.get().get();

    this.main = new HorizontalLayout();
    this.add(this.main);

    this.print = new VerticalLayout();
    this.main.add(this.print);

    this.print.setWidth(30, Unit.REM);

    this.createUploadSection();
    this.createPrintOptionsSection();
    this.createPrintSection();

    this.log = new VerticalLayout();
    this.main.add(this.log);

    this.createLogGrid();
  }

  private void createUploadSection() {
    this.uploadSection = new VerticalLayout();
    this.print.add(this.uploadSection);

    this.uploadSection.add(new H3("Files"));

    this.fileBuffer = new MultiFileBuffer();

    Upload upload = new Upload(this.fileBuffer);
    upload.setDropAllowed(true);
    upload.setAcceptedFileTypes("application/pdf", ".pdf");
    upload.setMaxFileSize(100 * 1024 * 1024);
    upload.setMaxFiles(5);

    upload.addFileRejectedListener(e -> {
      String errorMsg = e.getErrorMessage();
      Notification notification = Notification.show(errorMsg, 5000, Notification.Position.TOP_CENTER);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    });

    this.uploadSection.add(upload);
  }

  private void createPrintOptionsSection() {
    this.printOptionsSection = new VerticalLayout();
    this.print.add(this.printOptionsSection);

    this.printOptionsSection.add(new H3("Options"));

    HorizontalLayout h1 = new HorizontalLayout();
    this.printOptionsSection.add(h1);
    h1.addClassNames("flex_wrap");

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

    HorizontalLayout h2 = new HorizontalLayout();
    this.printOptionsSection.add(h2);
    h2.addClassNames("flex_wrap");

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
    h2.add(this.pageRange);
  }

  private void createPrintSection() {
    this.printSection = new VerticalLayout();
    this.print.add(this.printSection);

    Button print = new Button("Print");
    this.printSection.add(print);

    print.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    print.setDisableOnClick(true);
    print.setIcon(VaadinIcon.PRINT.create());

    print.addClickListener(e -> {

      List<PrintRequest> requests = new ArrayList<>(this.fileBuffer.getFiles().size());
      for (String name : this.fileBuffer.getFiles()) {
        FileData fileData = this.fileBuffer.getFileData(name);
        requests.add(printService.createRequest(this.user, fileData.getFile())
            .name(fileData.getFileName())
            .orientation(this.orientationRadio.getValue())
            .sides(this.sidesRadio.getValue())
            .perPage(this.perPageSelect.getValue())
            .range(PrintRequest.PageRange.fromString(this.pageRange.getValue()))
            .copies(this.quantity.getValue()));
      }

      this.printService.getExecutorService().execute(() -> {
        try {
          List<PrintResult> results = this.printService.process(requests, new PrintListener() {
            @Override
            public void onPrinting(PrintRequest request) {
              PrintView.this.getUI().get().access(() -> {
                Notification notification = new Notification();
                notification.setPosition(Notification.Position.TOP_CENTER);
                notification.setDuration(3000);
                notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
                notification.setText("Printing " + request.getName());
                notification.open();
              });
            }

            @Override
            public void onCompleted(PrintRequest request, PrintResult result) {
              PrintView.this.getUI().get().access(() -> {
                Notification notification = new Notification();
                notification.setPosition(Notification.Position.TOP_CENTER);
                notification.setDuration(3000);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                notification.setText("Finished " + request.getName());
                notification.open();
              });
            }

            @Override
            public void onError(PrintResult result) {
              PrintView.this.getUI().get().access(() -> {
                Notification notification = new Notification();
                notification.setPosition(Notification.Position.TOP_CENTER);
                notification.setDuration(3000);
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.setText("Error " + result.getRequest().getName() + ": " + result.getErrorType().getUserMessage());
                notification.open();
              });
            }
          }).get();
        } catch (InterruptedException | ExecutionException ex) {
          Application.getLogger().warning("Exception while waiting for request from user '" + this.user.getUsername() + "': " + ex.getMessage());
        }

        print.setEnabled(true);
      });
    });
  }

  public void createLogGrid() {
    // TODO log
  }
}
