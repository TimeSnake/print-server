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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

public class PrintRequest {

  final PrintService printService;

  private final PrintJob job;
  private final File file;
  private String name;
  private User user;
  private Printer printer;
  private PrintOrientation orientation;
  private PrintSides sides;
  private PrintPerPage perPage;
  private PageRange pageRange;
  private int copies;

  private Integer documentPages;
  private Integer selectedPages;
  private Integer printedPages;
  private Double price;

  PrintResult result;

  PrintStatus status;

  public PrintRequest(PrintService printService, File file) {
    this.printService = printService;
    this.job = new PrintJob();
    this.file = file;
    this.printer = printService.getDefaultPrinter();
    this.orientation = PrintOrientation.PORTRAIT;
    this.sides = PrintSides.ONE_SIDED;
    this.perPage = PrintPerPage.ONE;
    this.copies = 1;
    this.status = PrintStatus.CREATED;
  }

  public PrintRequest name(String name) {
    this.name = name;
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

  public PrintRequest range(PageRange range) {
    this.pageRange = range;
    this.update();
    return this;
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
        + (this.pageRange != null ? this.pageRange.getCmd() : "")
        + " -n " + this.copies;
  }

  private void update() {
    this.updateDocumentPages();
    this.updateSelectedPages();
    this.updatePrintedPages();
    this.updatePrice();
  }

  private void updateDocumentPages() {
    try {
      PDDocument doc = Loader.loadPDF(this.getFile());
      this.documentPages = doc.getNumberOfPages();
      doc.close();
    } catch (IOException e) {
      Application.getLogger().warning("Exception while getting number of pages for file '" + this.file.getName() + "': " + e.getMessage());
    }
  }

  private void updateSelectedPages() {
    if (this.pageRange == null) {
      this.selectedPages = this.documentPages;
      return;
    }

    this.selectedPages = this.pageRange.getPages().size();
  }

  private void updatePrintedPages() {
    if (this.selectedPages == null) {
      Application.getLogger().warning("Error while calculating pages for file '" + this.file.getName() + "': unknown document page number");
      return;
    }

    this.printedPages = this.getSides().numberOfPages().apply(this.selectedPages);
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

  public PrintResult start() {
    if (this.isRunning()) {
      return new PrintResult(this, PrintResult.ErrorType.ALREADY_RUNNING);
    }

    String cmd = this.buildCmd();

    this.result = new PrintResult(this);

    try {
      Application.getLogger().info("Printing file '" + this.getName() + "' from user '" + this.getUser().getName() + "'");
      Process process = Runtime.getRuntime().exec(cmd);

      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      String errorResult;
      while ((errorResult = errorReader.readLine()) != null) {
        Application.getLogger().warning("Error while executing job of user '" + this.file.getName() + "': " + errorResult);
      }

      StringBuilder outputResults = new StringBuilder();
      String outputResult;
      while ((outputResult = outputReader.readLine()) != null) {
        outputResults.append("\n").append(outputResult);
      }

      this.result.parseOutput(outputResults.toString());
    } catch (IOException e) {
      this.result.errorType = PrintResult.ErrorType.EXECUTION_EXCEPTION;
      this.status = PrintStatus.ERROR;
      Application.getLogger().warning("Error while executing job of user '" + this.file.getName() + "': " + e.getMessage());
      return this.result;
    }

    this.status = PrintStatus.PRINTING;

    return this.result;
  }

  public void complete(PrintResult result) {
    this.job.setCupsId(result.getCupsId());
    this.job.setFileName(this.file.getName());
    this.job.setDocumentPages(this.documentPages);
    this.job.setSelectedPages(this.selectedPages);
    this.job.setPrintedPages(this.printedPages);
    this.job.setCosts(this.price);
    this.job.setPrinter(this.printer);
    this.job.setUser(this.user);

    this.printService.getPrintJobRepository().save(this.job);
  }


  public PrintJob getJob() {
    return job;
  }

  public File getFile() {
    return file;
  }

  public String getName() {
    return name;
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

  public Integer getSelectedPages() {
    return selectedPages;
  }

  public PageRange getPageRange() {
    return pageRange;
  }

  public User getUser() {
    return user;
  }

  public PrintStatus getStatus() {
    return status;
  }

  public PrintResult getResult() {
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PrintRequest that = (PrintRequest) o;
    return Objects.equals(file, that.file);
  }

  @Override
  public int hashCode() {
    return Objects.hash(file);
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

  public enum PrintStatus {
    CREATED("created"),
    QUEUED("queued"),
    PRINTING("printing"),
    COMPLETED("completed"),
    ERROR("error");

    private final String name;

    PrintStatus(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static class PageRange {

    public static PageRange fromString(String s) {
      if (s == null) {
        return null;
      }

      try {
        s = s.replace(" ", "");

        List<Integer> pages = new LinkedList<>();
        for (String range : s.split(",")) {
          String[] bounds = range.split("-", 2);
          int lowerBound = Integer.parseInt(bounds[0]);
          int upperBound = lowerBound;

          if (bounds.length == 2) {
            upperBound = Integer.parseInt(bounds[1]);
          }

          IntStream.rangeClosed(lowerBound, upperBound).forEach(pages::add);
        }

        return new PageRange(s, pages);
      } catch (IndexOutOfBoundsException | NumberFormatException e) {
        return null;
      }
    }

    private final String cmd;
    private final List<Integer> pages;

    public PageRange(String cmd, List<Integer> pages) {
      this.cmd = cmd;
      this.pages = pages;
    }

    public List<Integer> getPages() {
      return pages;
    }

    public String getCmd() {
      return " -o page-range=" + cmd;
    }
  }
}
