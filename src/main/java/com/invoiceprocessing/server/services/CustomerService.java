package com.invoiceprocessing.server.services;

import com.invoiceprocessing.server.model.Customer;

import java.util.List;

public interface CustomerService {
    Customer addCustomer(Customer customer);

    List<Customer> getCustomers();

    Customer updateCustomer(long customerId, Customer customer);

    Customer deleteCustomer(long customerId);
}