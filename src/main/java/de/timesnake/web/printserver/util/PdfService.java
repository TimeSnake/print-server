/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.web.printserver.util;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import de.timesnake.web.printserver.Application;
import de.timesnake.web.printserver.data.entity.PrintJob;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PdfService {

  public String writeUserTotals(Collection<PrintJob> printJobs) throws IOException, DocumentException {
    String fileName = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "_user_totals.pdf";

    new File("api/users/totals/" + fileName).createNewFile();

    Document document = new Document();
    OutputStream fileOutputStream = new FileOutputStream("api/users/totals/" + fileName);
    PdfWriter.getInstance(document, fileOutputStream);

    document.open();

    document.addTitle(fileName);

    PdfPTable table = new PdfPTable(4);

    List.of("User", "Printed Pages", "Costs").forEach(columnTitle -> {
      PdfPCell header = new PdfPCell();
      header.setBackgroundColor(BaseColor.LIGHT_GRAY);
      header.setBorderWidth(2);
      header.setPhrase(new Phrase(columnTitle));
      table.addCell(header);
    });

    table.completeRow();

    printJobs.stream().collect(Collectors.groupingBy(PrintJob::getUser)).forEach((user, userJobs) -> {
          int printedPages = userJobs.stream().mapToInt(PrintJob::getPrintedPages).sum();
          double costs = userJobs.stream().mapToDouble(PrintJob::getCosts).sum();

          table.addCell(createCell(user.getName()));
          table.addCell(createCell(String.valueOf(printedPages)));
          table.addCell(createCell(new DecimalFormat("0.00").format(costs)));
          table.completeRow();
        });

    document.add(table);
    document.close();
    Application.getLogger().info("Saved user totals to file");

    return fileName;
  }

  public File convertJpg2Pdf(File srcFile) throws DocumentException, IOException {
    File dstFile = new File(srcFile.getPath() + ".pdf");

    Document document = new Document();
    FileOutputStream fos = new FileOutputStream(dstFile);

    PdfWriter writer = PdfWriter.getInstance(document, fos);
    writer.open();
    document.open();
    document.add(Image.getInstance(srcFile.getName()));
    document.close();
    writer.close();

    return dstFile;
  }

  private static PdfPCell createCell(String value) {
    PdfPCell cell = new PdfPCell();
    cell.setPhrase(new Phrase(value));
    cell.setBackgroundColor(BaseColor.WHITE);
    return cell;
  }
}
