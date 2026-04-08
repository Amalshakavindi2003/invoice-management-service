package com.invoiceprocessing.server.dao;

import com.invoiceprocessing.server.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDao extends JpaRepository<Customer, Long> {
}