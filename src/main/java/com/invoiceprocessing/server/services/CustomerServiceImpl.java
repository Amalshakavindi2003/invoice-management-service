package com.invoiceprocessing.server.services;

import com.invoiceprocessing.server.dao.CustomerDao;
import com.invoiceprocessing.server.dao.InvoiceDao;
import com.invoiceprocessing.server.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private InvoiceDao invoiceDao;

    @Override
    public Customer addCustomer(Customer customer) {
        validateCustomer(customer);
        return customerDao.save(customer);
    }

    @Override
    public List<Customer> getCustomers() {
        return customerDao.findAll();
    }

    @Override
    public Customer updateCustomer(long customerId, Customer customer) {
        Customer existing = customerDao.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        validateCustomer(customer);

        existing.setName(customer.getName().trim());
        existing.setEmail(customer.getEmail().trim());
        existing.setPhone(customer.getPhone().trim());
        existing.setAddress(customer.getAddress().trim());

        return customerDao.save(existing);
    }

    @Override
    public Customer deleteCustomer(long customerId) {
        Customer customer = customerDao.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        if (invoiceDao.countByCustomerId(customerId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete customer linked to invoices");
        }

        customerDao.delete(customer);
        return customer;
    }

    private void validateCustomer(Customer customer) {
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer payload is required");
        }

        if (isBlank(customer.getName()) || isBlank(customer.getEmail()) || isBlank(customer.getPhone()) || isBlank(customer.getAddress())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email, phone and address are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}