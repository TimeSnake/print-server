package de.timesnake.web.printserver.util;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.open.App;
import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.PrinterRepository;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.data.service.PrintJobRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

@Service
public class PrintService {

  private final ExecutorService executorService = new ThreadPoolExecutor(5, 100, 5L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>());

  private final PrintJobRepository printJobRepository;
  private final PrinterRepository printerRepository;

  public PrintService(PrintJobRepository printJobRepository, PrinterRepository printerRepository) {
    this.printJobRepository = printJobRepository;
    this.printerRepository = printerRepository;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public Printer getDefaultPrinter() {
    return this.printerRepository.findAll().stream().min(Comparator.comparing(Printer::getPriority)).orElse(null);
  }

  public PrintJobRepository getPrintJobRepository() {
    return printJobRepository;
  }

  public PrinterRepository getPrinterRepository() {
    return printerRepository;
  }

  public PrintRequest createRequest(File file) {
    return new PrintRequest(this, file);
  }

  public Future<List<PrintResult>> process(List<PrintRequest> requests, PrintListener printListener) {
    return this.getExecutorService().submit(() -> this.processSync(requests, printListener));
  }

  private List<PrintResult> processSync(List<PrintRequest> requests, PrintListener printListener) {
    Map<PrintRequest, Future<PrintResult>> futures = new HashMap<>(requests.size());
    for (PrintRequest request : requests) {
      futures.put(request, this.getExecutorService().submit(() -> {
        PrintResult res = request.start();

        if (!res.hasError()) {
          printListener.onPrinting(request);
          res.waitForCompletion();
          printListener.onCompleted(request, res);
        } else {
          printListener.onError(res);
        }
        return res;
      }));
    }

    List<PrintResult> results = new ArrayList<>(futures.size());
    for (Map.Entry<PrintRequest, Future<PrintResult>> entry : futures.entrySet()) {
      try {
        results.add(entry.getValue().get());
      } catch (InterruptedException | ExecutionException e) {
        Application.getLogger().warning("Exception while waiting for result of file '" + entry.getKey().getName()
            + "' of user '" + entry.getKey().getUser().getUsername() + "': " + e.getMessage());
        results.add(new PrintResult(entry.getKey(), PrintResult.ErrorType.EXECUTION_EXCEPTION));
      }
    }

    return results;
  }
}
