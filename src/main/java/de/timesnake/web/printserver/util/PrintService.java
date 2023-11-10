package de.timesnake.web.printserver.util;

import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.PrinterRepository;
import de.timesnake.web.printserver.data.entity.User;
import de.timesnake.web.printserver.data.service.PrintJobRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collector;

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

  public CompletionService<PrintResult> print(List<PrintRequest> printRequests) {
    CompletionService<PrintResult> completionService = new ExecutorCompletionService<>(this.executorService);
    for (PrintRequest printRequest : printRequests) {
      completionService.submit(printRequest::run);
    }
    return completionService;
  }

  public Future<PrintResult> print(PrintRequest printRequest) {
    return this.executorService.submit(printRequest::run);
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

  public PrintRequest createRequest(User user, File file) {
    return new PrintRequest(this)
        .user(user)
        .file(file);
  }
}
