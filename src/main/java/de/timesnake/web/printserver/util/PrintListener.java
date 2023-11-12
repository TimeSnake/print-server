package de.timesnake.web.printserver.util;

public interface PrintListener {

  void onPrinting(PrintRequest request);

  void onCompleted(PrintRequest request, PrintResult result);

  void onError(PrintResult result);
}
