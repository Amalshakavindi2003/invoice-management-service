package com.invoiceprocessing.server.services;

import com.invoiceprocessing.server.dao.CustomerDao;
import com.invoiceprocessing.server.dao.InvoiceDao;
import com.invoiceprocessing.server.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private InvoiceDao invoiceDao;

    @Override
    public Customer addCustomer(Customer customer) {
        Customer normalized = normalizeAndValidate(customer);

        if (customerDao.existsByEmailIgnoreCase(normalized.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already used by another customer");
        }

        normalized.setReferenceCode(null);
        Customer saved = customerDao.save(normalized);
        ensureReferenceCode(saved);
        return customerDao.save(saved);
    }

    @Override
    public List<Customer> getCustomers() {
        List<Customer> customers = customerDao.findAll();
        boolean changed = false;

        for (Customer customer : customers) {
            changed = ensureReferenceCode(customer) || changed;
        }

        if (changed) {
            customerDao.saveAll(customers);
        }

        return customers;
    }

    @Override
    public Customer updateCustomer(long customerId, Customer customer) {
        Customer existing = customerDao.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        Customer normalized = normalizeAndValidate(customer);

        if (customerDao.existsByEmailIgnoreCaseAndIdNot(normalized.getEmail(), customerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already used by another customer");
        }

        existing.setName(normalized.getName());
        existing.setEmail(normalized.getEmail());
        existing.setPhone(normalized.getPhone());
        existing.setAddress(normalized.getAddress());
        ensureReferenceCode(existing);

        return customerDao.save(existing);
    }

    @Override
    public Customer deleteCustomer(long customerId) {
        Customer customer = customerDao.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        long invoiceCount = invoiceDao.countByCustomerId(customerId);
        if (invoiceCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete customer with linked invoices. Mark invoices done first."
            );
        }

        customerDao.delete(customer);
        return customer;
    }

    private Customer normalizeAndValidate(Customer customer) {
        if (customer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer payload is required");
        }

        String name = normalize(customer.getName());
        String email = normalize(customer.getEmail());
        String phone = normalize(customer.getPhone());
        String address = normalize(customer.getAddress());

        if (isBlank(name) || isBlank(email) || isBlank(phone) || isBlank(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email, phone and address are required");
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email format is invalid");
        }

        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(address);
        return customer;
    }

    private boolean ensureReferenceCode(Customer customer) {
        if (customer == null || customer.getId() <= 0) {
            return false;
        }

        if (!isBlank(customer.getReferenceCode())) {
            return false;
        }

        customer.setReferenceCode(formatReferenceCode(customer.getId()));
        return true;
    }

    private String formatReferenceCode(long id) {
        return String.format("CUS-%06d", id);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}