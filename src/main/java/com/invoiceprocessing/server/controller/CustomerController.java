package com.invoiceprocessing.server.controller;

import com.invoiceprocessing.server.model.Customer;
import com.invoiceprocessing.server.services.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @PostMapping("/customer")
    public Customer addCustomer(@RequestBody Customer customer) {
        return customerService.addCustomer(customer);
    }

    @GetMapping("/customer")
    public List<Customer> getCustomers() {
        return customerService.getCustomers();
    }

    @PutMapping("/customer/{customerId}")
    public Customer updateCustomer(@PathVariable String customerId, @RequestBody Customer customer) {
        return customerService.updateCustomer(Long.parseLong(customerId), customer);
    }

    @DeleteMapping("/customer/{customerId}")
    public Customer deleteCustomer(@PathVariable String customerId) {
        return customerService.deleteCustomer(Long.parseLong(customerId));
    }
}