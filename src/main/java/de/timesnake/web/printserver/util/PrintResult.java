package de.timesnake.web.printserver.util;

public class PrintResult {

  private final PrintRequest request;

  private String cupsId;
  private ErrorType errorType;

  public PrintResult(PrintRequest request) {
    this.request = request;
  }

  public PrintResult(PrintRequest request, ErrorType errorType) {
    this.request = request;
    this.errorType = errorType;
  }

  public void parseOutput(String result) {
    if (result.contains("request id is ")) {
      this.cupsId = result.substring(result.indexOf("request id is ")).split(" ")[0];
    }

    if (this.cupsId == null || this.cupsId.isBlank()) {
      errorType = ErrorType.NO_CUPS_ID;
    }

    System.out.println(String.join("\n", result));
  }

  public String getCupsId() {
    return cupsId;
  }

  public boolean hasError() {
    return this.errorType != null;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public enum ErrorType {
    NO_CUPS_ID("unable to determine print job", "Error (no_cups_id)"),
    EXECUTION_EXCEPTION("exception while waiting for result", "Error (execution_exception)"),
    ALREADY_RUNNING("job already running", "Error (already_running)");

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
