package de.timesnake.web.printserver.views.print;

import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileData;
import com.vaadin.flow.component.upload.receivers.MultiFileBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.security.AuthenticatedUser;
import de.timesnake.web.printserver.util.PrintRequest;
import de.timesnake.web.printserver.util.PrintResult;
import de.timesnake.web.printserver.util.PrintService;
import de.timesnake.web.printserver.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;

@RolesAllowed(value = {"USER"})
@PageTitle("Print")
@Route(value = "print", layout = MainLayout.class)
public class PrintView extends Div {

  private final PrintService printService;
  private final User user;

  private final VerticalLayout main;
  private VerticalLayout uploadSection;
  private VerticalLayout printOptionsSection;
  private VerticalLayout printSection;

  private RadioButtonGroup<PrintRequest.PrintOrientation> orientationRadio;
  private RadioButtonGroup<PrintRequest.PrintSides> sidesRadio;
  private Select<PrintRequest.PrintPerPage> perPageSelect;
  private IntegerField quantity;

  private final List<File> files = new LinkedList<>();

  public PrintView(PrintService printService, AuthenticatedUser user) {
    this.printService = printService;
    this.user = user.get().get();

    this.main = new VerticalLayout();
    this.add(this.main);

    this.main.setWidth(30, Unit.REM);

    this.createUploadSection();
    this.createPrintOptionsSection();
    this.createPrintSection();
  }

  private void createUploadSection() {
    this.uploadSection = new VerticalLayout();
    this.main.add(this.uploadSection);

    this.uploadSection.add(new H3("Files"));

    MultiFileBuffer buffer = new MultiFileBuffer();

    Upload upload = new Upload(buffer);
    upload.setDropAllowed(true);
    upload.setAcceptedFileTypes("application/pdf", ".pdf");
    upload.setMaxFileSize(100 * 1024 * 1024);
    upload.setMaxFiles(10);

    upload.addFileRejectedListener(e -> {
      String errorMsg = e.getErrorMessage();
      Notification notification = Notification.show(errorMsg, 5000, Notification.Position.TOP_CENTER);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    });

    upload.addSucceededListener(event -> {
      String uploadFileName = event.getFileName();
      FileData savedFileData = buffer.getFileData(uploadFileName);
      String absolutePath = savedFileData.getFile().getAbsolutePath();

      files.add(savedFileData.getFile());

      System.out.println("File saved to: " + absolutePath);
    });

    this.uploadSection.add(upload);
  }

  private void createPrintOptionsSection() {
    this.printOptionsSection = new VerticalLayout();
    this.main.add(this.printOptionsSection);

    this.printOptionsSection.add(new H3("Options"));

    this.orientationRadio = new RadioButtonGroup<>();
    this.orientationRadio.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
    this.orientationRadio.setLabel("Orientation");
    this.orientationRadio.setItems(PrintRequest.PrintOrientation.values());
    this.orientationRadio.setValue(PrintRequest.PrintOrientation.PORTRAIT);
    this.printOptionsSection.add(this.orientationRadio);

    this.sidesRadio = new RadioButtonGroup<>();
    this.sidesRadio.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
    this.sidesRadio.setLabel("Sides");
    this.sidesRadio.setItems(PrintRequest.PrintSides.values());
    this.sidesRadio.setValue(PrintRequest.PrintSides.ONE_SIDED);
    this.printOptionsSection.add(this.sidesRadio);

    this.perPageSelect = new Select<>();
    this.perPageSelect.setLabel("Pages per page");
    this.perPageSelect.setItems(PrintRequest.PrintPerPage.values());
    this.perPageSelect.setValue(PrintRequest.PrintPerPage.ONE);
    this.printOptionsSection.add(this.perPageSelect);

    this.quantity = new IntegerField();
    this.quantity.setLabel("Quantity");
    this.quantity.setHelperText("Max 10 items");
    this.quantity.setMin(1);
    this.quantity.setMax(10);
    this.quantity.setValue(1);
    this.quantity.setStepButtonsVisible(true);
    this.printOptionsSection.add(quantity);
  }

  private void createPrintSection() {
    this.printSection = new VerticalLayout();
    this.main.add(this.printSection);

    Button print = new Button("Print");
    this.printSection.add(print);

    print.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    print.setDisableOnClick(true);
    print.setIcon(VaadinIcon.PRINT.create());

    print.addClickListener(e -> {

      List<PrintRequest> requests = new ArrayList<>(this.files.size());
      for (File file : this.files) {
        requests.add(printService.createRequest(this.user, file)
            .orientation(this.orientationRadio.getValue())
            .sides(this.sidesRadio.getValue())
            .perPage(this.perPageSelect.getValue())
            .copies(this.quantity.getValue()));
      }

      for (PrintRequest request : requests) {
        PrintResult result;
        try {
          result = this.printService.print(request).get();
        } catch (InterruptedException | ExecutionException ex) {
          result = new PrintResult(request, PrintResult.ErrorType.EXECUTION_EXCEPTION);
        }

        Notification notification = new Notification();
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(3000);

        if (result.hasError()) {
          notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
          notification.setText(result.getErrorType().getUserMessage());
        } else {
          notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          notification.setText("Job done");
        }

        notification.open();
      }



      print.setEnabled(true);
    });
  }
}
