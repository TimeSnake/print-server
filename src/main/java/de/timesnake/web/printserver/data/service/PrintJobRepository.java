package de.timesnake.web.printserver.data.service;

import com.vaadin.flow.component.page.Page;
import de.timesnake.web.printserver.data.entity.PrintJob;
import de.timesnake.web.printserver.data.entity.Printer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

  List<PrintJob> getPrintJobsByPrinter(Pageable pageable, Printer printer);
}