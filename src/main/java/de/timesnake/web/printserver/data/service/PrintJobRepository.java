package de.timesnake.web.printserver.data.service;

import de.timesnake.web.printserver.data.entity.PrintJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {
}