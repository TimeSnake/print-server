package de.timesnake.web.printserver.util;

import de.timesnake.web.printserver.Application;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class PrintResult {

  private final PrintRequest request;

  private String cupsId;
  ErrorType errorType;

  private int pagesPrinted = 0;

  public PrintResult(PrintRequest request) {
    this.request = request;
  }

  public PrintResult(PrintRequest request, ErrorType errorType) {
    this.request = request;
    this.errorType = errorType;
  }

  public void parseOutput(String result) {
    if (result.contains("request id is ")) {
      this.cupsId = result.replace("request id is ", "").replaceAll("\n", "").split(" ")[0];
      Application.getLogger().info("CUPS id of file '" + this.request.getName() + "' from user '" +
          this.request.getUser().getUsername() + "': " + this.cupsId);
    }

    if (this.cupsId == null || this.cupsId.isBlank()) {
      this.errorType = ErrorType.NO_CUPS_ID;
      this.request.status = PrintRequest.PrintStatus.ERROR;
    }
  }

  public void syncUpdates(PrintListener listener) {
    try {
      File logFile = this.getRequest().printService.getCupsLogFile();
      WatchService watcher = FileSystems.getDefault().newWatchService();
      logFile.toPath().register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
      WatchKey key;

      while((key = watcher.poll(10, TimeUnit.SECONDS)) != null) {
        for (WatchEvent<?> event : key.pollEvents()) {
          if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            boolean jobDone = this.checkLog(logFile, listener);
            if (jobDone) {
              return;
            }
          }
        }
        boolean valid = key.reset();

        if (!valid) {
          return;
        }
      }

      this.errorType = ErrorType.TIME_OUT;
    } catch (IOException | InterruptedException e) {
      this.request.status = PrintRequest.PrintStatus.ERROR;
      Application.getLogger().warning("Error while waiting for job completion from user '" + this.request.getUser().getUsername() + "': " + e.getMessage());
    }
  }

  private boolean checkLog(File logFile, PrintListener listener) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)));

    String line;
    while ((line = reader.readLine()) != null) {
      try {
        String[] values = line.split(",");
        String printer = values[0];
        String jobId = values[1];

        if (!this.cupsId.equals(printer + "-" + jobId)) {
          continue;
        }

        this.pagesPrinted = Integer.parseInt(values[7]);

        listener.onPrintUpdate(this.request, this);

        String currentPageNumber = values[3];

        return currentPageNumber.equals("total");
      } catch (NumberFormatException | IndexOutOfBoundsException ignored) {

      }
    }

    return false;
  }


  public void waitForCompletion() {
    Application.getLogger().info("Waiting for completion of file '" + this.request.getName() + "' from user '" +
        this.request.getUser().getUsername() + "'");

    try {
      int timeoutCounterSec = 0;
      StringBuilder result;

      do {
        if (timeoutCounterSec > 10) {
          this.errorType = ErrorType.TIME_OUT;
          break;
        }

        Thread.sleep(1000);

        Process process = Runtime.getRuntime().exec("lpstat -W completed | grep " + this.cupsId);

        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String errorResult;
        while ((errorResult = errorReader.readLine()) != null) {
          Application.getLogger().warning("Error while waiting for job completion from user '" + this.request.getUser().getUsername() + "': " + errorResult);
        }

        result = new StringBuilder();
        String line;
        while ((line  = outputReader.readLine()) != null) {
          result.append(line);
        }

        timeoutCounterSec++;
      } while (!result.toString().contains(this.cupsId));
    } catch (IOException | InterruptedException e) {
      this.request.status = PrintRequest.PrintStatus.ERROR;
      Application.getLogger().warning("Error while waiting for job completion from user '" + this.request.getUser().getUsername() + "': " + e.getMessage());
      return;
    }

    this.complete();
    this.request.status = PrintRequest.PrintStatus.COMPLETED;

    Application.getLogger().info("Completed job '" + this.cupsId + "' for file '" + this.request.getName() + "' from user '" + this.request.getUser().getUsername() + "'");
  }

  public String getCupsId() {
    return cupsId;
  }

  public boolean hasError() {
    return this.errorType != null;
  }

  public PrintRequest getRequest() {
    return request;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public void complete() {
    this.request.complete(this);
  }

  public int getPagesPrinted() {
    return pagesPrinted;
  }

  public enum ErrorType {
    NO_CUPS_ID("unable to determine print job", "no_cups_id"),
    EXECUTION_EXCEPTION("exception while waiting for result", "execution_exception"),
    ALREADY_RUNNING("job already running", "already_running"),
    TIME_OUT("timed out", "time_out"),
    FILE_CONVERT("unable to convert file", "file_convert"),
    PAGE_COUNT("unable to calculate page number", "page_count");

    private final String message;
    private final String userMessage;

    ErrorType(String message, String userMessage) {
      this.message = message;
      this.userMessage = userMessage;
    }

    public String getMessage() {
      return message;
    }

    public String getUserMessage() {
      return userMessage;
    }
  }
}
