package de.timesnake.web.printserver.util;

import com.itextpdf.text.DocumentException;
import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import de.timesnake.web.printserver.data.entity.User;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

public class PrintRequest {

  final PrintService printService;

  private final PrintJob job;
  private final File srcFile;
  private File resFile;
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

  public PrintRequest(PrintService printService, File srcFile) {
    this.printService = printService;
    this.job = new PrintJob();
    this.srcFile = srcFile;
    this.printer = printService.getDefaultPrinter();
    this.orientation = PrintOrientation.PORTRAIT;
    this.sides = PrintSides.ONE_SIDED;
    this.perPage = PrintPerPage.ONE;
    this.copies = 1;
    this.status = PrintStatus.CREATED;

    try {
      this.resFile = this.transformFile(srcFile);
    } catch (DocumentException | IOException e) {
      Application.getLogger().warning("Exception while converting file '" + this.getName() + "' of user '" +
          this.user.getUsername() + "': " + e.getMessage());
      this.result = new PrintResult(this, PrintResult.ErrorType.FILE_CONVERT);
    }
  }

  private File transformFile(File srcFile) throws DocumentException, IOException {
    String ext = FilenameUtils.getExtension(srcFile.getName());
    return switch (ext) {
      case "pdf" -> srcFile;
      case "jpg", "jpeg" -> this.printService.getPdfService().convertJpg2Pdf(srcFile);
      default -> srcFile;
    };
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
    return "lp " + this.resFile.getAbsolutePath()
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
      PDDocument doc = Loader.loadPDF(this.getResFile());
      this.documentPages = doc.getNumberOfPages();
      doc.close();
    } catch (IOException e) {
      Application.getLogger().warning("Exception while getting number of pages for file '" +
          this.resFile.getName() + "' from user '" + this.user.getUsername() + "': " + e.getMessage());
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
      Application.getLogger().warning("Error while calculating pages for file '" + this.resFile.getName() +
          "' from user '" + this.user.getUsername() + "': unknown document page number");
      return;
    }

    this.printedPages = this.getSides().numberOfPages().apply(this.selectedPages);
    this.printedPages = this.getPerPage().numberOfPages().apply(this.printedPages);
    this.printedPages = this.printedPages * this.getCopies();
  }

  private void updatePrice() {
    if (this.printedPages == null) {
      Application.getLogger().warning("Error while calculating price for file '" + this.resFile.getName() +
          "' from user '" + this.user.getUsername() + "': unknown printed page number");
      return;
    }

    this.price = switch (this.sides) {
      case ONE_SIDED -> this.printedPages * this.printer.getPriceOneSided();
      case TWO_SIDED_LONG_EDGE, TWO_SIDED_SHORT_EDGE -> this.printedPages * this.printer.getPriceTwoSided();
    };
  }

  public PrintResult start() {
    if (this.result != null) {
      return this.result;
    }

    if (this.isRunning()) {
      this.result = new PrintResult(this, PrintResult.ErrorType.ALREADY_RUNNING);
      return this.result;
    }

    if (this.printedPages == null) {
      this.result = new PrintResult(this, PrintResult.ErrorType.PAGE_COUNT);
      return this.result;
    }

    String cmd = this.buildCmd();

    this.result = new PrintResult(this);

    try {
      Application.getLogger().info("Printing file '" + this.getName() + "' from user '" +
          this.getUser().getUsername() + "'");
      Process process = Runtime.getRuntime().exec(cmd);

      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      String errorResult;
      while ((errorResult = errorReader.readLine()) != null) {
        Application.getLogger().warning("Error while executing job '" + this.getName() + "' of user '" +
            this.getUser().getUsername() + "': " + errorResult);
      }

      StringBuilder outputResults = new StringBuilder();
      String outputResult;
      while ((outputResult = outputReader.readLine()) != null) {
        outputResults.append(System.lineSeparator()).append(outputResult);
      }

      this.status = PrintStatus.PRINTING;
      this.result.parseOutput(outputResults.toString());
    } catch (IOException e) {
      this.result.errorType = PrintResult.ErrorType.EXECUTION_EXCEPTION;
      this.status = PrintStatus.ERROR;
      Application.getLogger().warning("Error while executing job '" + this.getName() + "' of user '" +
          this.getUser().getUsername() + "': " + e.getMessage());
    }

    return this.result;
  }

  public void complete(PrintResult result) {
    this.job.setCupsId(result.getCupsId());
    this.job.setFileName(this.name);
    this.job.setDocumentPages(this.documentPages);
    this.job.setSelectedPages(this.selectedPages);
    this.job.setPrintedPages(this.printedPages);
    this.job.setCosts(this.price);
    this.job.setPrinter(this.printer);
    this.job.setUser(this.user);
    this.job.setTimestamp(LocalDateTime.now());

    this.printService.getPrintJobRepository().save(this.job);
  }


  public PrintJob getJob() {
    return job;
  }

  public File getSrcFile() {
    return srcFile;
  }

  public File getResFile() {
    return resFile;
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
    return Objects.equals(srcFile, that.srcFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(srcFile);
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
