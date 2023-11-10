package de.timesnake.web.printserver.util;

import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Function;

public class PrintRequest {

  private final PrintService printService;

  private final PrintJob job;
  private File file;
  private User user;
  private Printer printer;
  private PrintOrientation orientation;
  private PrintSides sides;
  private PrintPerPage perPage;
  private int copies;

  private Integer documentPages;
  private Integer printedPages;
  private Double price;

  private PrintResult result;

  public PrintRequest(PrintService printService) {
    this.printService = printService;
    this.job = new PrintJob();
    this.printer = printService.getDefaultPrinter();
    this.orientation = PrintOrientation.PORTRAIT;
    this.sides = PrintSides.ONE_SIDED;
    this.perPage = PrintPerPage.ONE;
    this.copies = 1;
  }

  public PrintRequest file(File file) {
    this.file = file;
    this.update();
    return this;
  }

  public PrintRequest user(User user) {
    this.user = user;
    return this;
  }

  public PrintRequest printer(Printer printer) {
    this.printer = printer;
    this.update();
    return this;
  }

  public PrintRequest orientation(PrintOrientation orientation) {
    this.orientation = orientation;
    return this;
  }

  public PrintRequest sides(PrintSides sides) {
    this.sides = sides;
    this.update();
    return this;
  }

  public PrintRequest copies(int copies) {
    this.copies = copies;
    this.update();
    return this;
  }

  public PrintRequest perPage(PrintPerPage perPage) {
    this.perPage = perPage;
    this.update();
    return this;
  }

  public PrintJob getJob() {
    return job;
  }

  public File getFile() {
    return file;
  }

  public Printer getPrinter() {
    return printer;
  }

  public PrintOrientation getOrientation() {
    return orientation;
  }

  public PrintSides getSides() {
    return sides;
  }

  public PrintPerPage getPerPage() {
    return perPage;
  }

  public int getCopies() {
    return copies;
  }

  public Integer getDocumentPages() {
    return documentPages;
  }

  public Integer getPrintedPages() {
    return printedPages;
  }

  public Double getPrice() {
    return price;
  }

  public boolean isRunning() {
    return this.result != null;
  }

  private String buildCmd() {
    return "lp " + this.file.getAbsolutePath()
        //+ " -P " + this.printer
        + this.orientation.getCmd()
        + this.sides.getCmd()
        + this.perPage.getCmd()
        + " -n " + this.copies;
  }

  private void update() {
    this.updateDocumentPageNumber();
    this.updatePrintedPages();
    this.updatePrice();
  }

  private void updateDocumentPageNumber() {
    try {
      PDDocument doc = Loader.loadPDF(this.getFile());
      this.documentPages = doc.getNumberOfPages();
      doc.close();
    } catch (IOException e) {
      Application.getLogger().warning("Exception while getting number of pages for file '" + this.file.getName() + "': " + e.getMessage());
    }
  }

  private void updatePrintedPages() {
    if (this.documentPages == null) {
      Application.getLogger().warning("Error while calculating pages for file '" + this.file.getName() + "': unknown document page number");
      return;
    }

    this.printedPages = this.getSides().numberOfPages().apply(this.documentPages);
    this.printedPages = this.getPerPage().numberOfPages().apply(this.printedPages);
    this.printedPages = this.printedPages * this.getCopies();
  }

  private void updatePrice() {
    if (this.printedPages == null) {
      Application.getLogger().warning("Error while calculating price for file '" + this.file.getName() + "': unknown printed page number");
      return;
    }

    this.price = switch (this.sides) {
      case ONE_SIDED -> this.printedPages * this.printer.getPriceOneSided();
      case TWO_SIDED_LONG_EDGE, TWO_SIDED_SHORT_EDGE -> this.printedPages * this.printer.getPriceTwoSided();
    };
  }

  protected PrintResult run() {
    if (this.isRunning()) {
      return new PrintResult(this, PrintResult.ErrorType.ALREADY_RUNNING);
    }

    String cmd = this.buildCmd();

    this.result = new PrintResult(this);

    try {
      Process process = Runtime.getRuntime().exec(cmd);

      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      String errorResult;
      while ((errorResult = errorReader.readLine()) != null) {
        Application.getLogger().warning("Error while printing file '" + this.file.getName() + "': " + errorResult);
      }

      StringBuilder outputResults = new StringBuilder();
      String outputResult;
      while ((outputResult = outputReader.readLine()) != null) {
        outputResults.append("\n").append(outputResult);
      }

      result.parseOutput(outputResults.toString());
    } catch (IOException e) {
      Application.getLogger().warning("Error while printing file '" + this.file.getName() + "': " + e.getMessage());
    }

    if (!result.hasError()) {
      this.complete(result);
    }

    return result;
  }

  private void complete(PrintResult result) {
    this.job.setCupsId(result.getCupsId());
    this.job.setFileName(this.file.getName());
    this.job.setDocumentPages(this.documentPages);
    this.job.setPrintedPages(this.printedPages);
    this.job.setCosts(this.price);
    this.job.setPrinter(this.printer);
    this.job.setUser(this.user);

    this.printService.getPrintJobRepository().save(this.job);
  }


  public enum PrintOrientation {
    PORTRAIT("portrait", ""),
    LANDSCAPE("landscape", " -o landscape");

    private final String name;
    private final String cmd;

    PrintOrientation(String name, String cmd) {
      this.name = name;
      this.cmd = cmd;
    }

    public String getCmd() {
      return cmd;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum PrintSides {
    ONE_SIDED("one-sided", "one-sided", n -> n),
    TWO_SIDED_SHORT_EDGE("two-sided, short edge", "two-sided-short-edge", n -> (n + 1) / 2),
    TWO_SIDED_LONG_EDGE("two-sided, long edge", "two-sided-long-edge", n -> (n + 1) / 2);

    private final String name;
    private final String cmd;
    private final Function<Integer, Integer> numberOfPages;

    PrintSides(String name, String cmd, Function<Integer, Integer> numberOfPages) {
      this.name = name;
      this.cmd = cmd;
      this.numberOfPages = numberOfPages;
    }

    public String getCmd() {
      return " -o sides=" + cmd;
    }

    public Function<Integer, Integer> numberOfPages() {
      return numberOfPages;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum PrintPerPage {
    ONE("1", "1", n -> n),
    TWO("2", "2", n -> (n + 1) / 2),
    FOUR("4", "4", n -> (n + 3) / 4),
    EIGHT("8", "8", n -> (n + 7) / 8),
    SIXTEEN("16", "16", n -> (n + 15) / 16);

    private final String name;
    private final String cmd;
    private final Function<Integer, Integer> numberOfPages;

    PrintPerPage(String name, String cmd, Function<Integer, Integer> numberOfPages) {
      this.name = name;
      this.cmd = cmd;
      this.numberOfPages = numberOfPages;
    }

    public String getCmd() {
      return " -o number-up=" + cmd;
    }

    public Function<Integer, Integer> numberOfPages() {
      return numberOfPages;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
