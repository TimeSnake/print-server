package de.timesnake.web.printserver.data.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity(name = "print_job")
public class PrintJob {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "cups_id", nullable = false)
  private String cupsId;

  @Column(name = "document_pages", nullable = false)
  private Integer documentPages;

  @Column(name = "selected_pages", nullable = false)
  private Integer selectedPages;

  @Column(name = "printed_pages", nullable = false)
  private Integer printedPages;

  @Column(name = "costs", nullable = false)
  private Double costs;

  @ManyToOne(optional = false)
  @JoinColumn(name = "printer_id", nullable = false)
  private Printer printer;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "timestamp", nullable = false)
  private LocalDateTime timestamp;

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Printer getPrinter() {
    return printer;
  }

  public void setPrinter(Printer printer) {
    this.printer = printer;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public Double getCosts() {
    return costs;
  }

  public void setCosts(Double costs) {
    this.costs = costs;
  }

  public Integer getPrintedPages() {
    return printedPages;
  }

  public void setPrintedPages(Integer printedPages) {
    this.printedPages = printedPages;
  }

  public String getCupsId() {
    return cupsId;
  }

  public void setCupsId(String cupsId) {
    this.cupsId = cupsId;
  }

  public Integer getDocumentPages() {
    return documentPages;
  }

  public Integer getSelectedPages() {
    return selectedPages;
  }

  public void setSelectedPages(Integer selectedPages) {
    this.selectedPages = selectedPages;
  }

  public void setDocumentPages(Integer documentPages) {
    this.documentPages = documentPages;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
