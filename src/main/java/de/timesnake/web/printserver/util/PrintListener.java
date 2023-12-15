package de.timesnake.web.printserver.util;

public interface PrintListener {

  void onPrinting(PrintRequest request);

  void onPrintUpdate(PrintRequest request, PrintResult result);

  void onCompleted(PrintRequest request, PrintResult result);

  void onError(PrintResult result);
}
