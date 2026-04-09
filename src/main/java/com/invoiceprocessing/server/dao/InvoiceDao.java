package com.invoiceprocessing.server.dao;

import com.invoiceprocessing.server.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceDao extends JpaRepository<Invoice, Long> {
    long countByCustomerId(long customerId);

    List<Invoice> findByCustomerId(long customerId);
}
