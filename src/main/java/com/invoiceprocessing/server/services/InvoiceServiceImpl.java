package com.invoiceprocessing.server.services;

import com.invoiceprocessing.server.dao.CustomerDao;
import com.invoiceprocessing.server.dao.InvoiceDao;
import com.invoiceprocessing.server.model.Customer;
import com.invoiceprocessing.server.model.Invoice;
import com.invoiceprocessing.server.model.InvoiceLineItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    @Autowired
    private InvoiceDao invoiceDao;

    @Autowired
    private CustomerDao customerDao;

    @Override
    public Invoice addInvoice(Invoice invoice) {
        if (invoice == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice payload is required");
        }

        Customer customer = resolveCustomer(invoice);

        if (invoice.getDate() == null || invoice.getDate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice date is required");
        }

        List<InvoiceLineItem> safeItems = invoice.getLineItems() == null ? new ArrayList<>() : invoice.getLineItems();
        if (safeItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one line item is required");
        }

        double subtotal = 0;
        double taxTotal = 0;

        for (InvoiceLineItem item : safeItems) {
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each line item requires a name");
            }

            int quantity = item.getQuantity();
            double unitPrice = item.getUnitPrice();
            double taxRate = Math.max(0, item.getTaxRate());

            if (quantity <= 0 || unitPrice <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity and unit price must be greater than zero");
            }

            item.setName(item.getName().trim());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setTaxRate(taxRate);

            double lineSubtotal = quantity * unitPrice;
            double lineTax = lineSubtotal * (taxRate / 100.0);

            subtotal += lineSubtotal;
            taxTotal += lineTax;
        }

        double discount = Math.max(0, invoice.getDiscount());
        double totalAmount = Math.max(0, subtotal + taxTotal - discount);

        invoice.setCustomer(customer);
        invoice.setLineItems(safeItems);
        invoice.setSubtotal(subtotal);
        invoice.setTaxTotal(taxTotal);
        invoice.setDiscount(discount);
        invoice.setTotalAmount(totalAmount);

        if (invoice.getAction() == null || invoice.getAction().isBlank()) {
            invoice.setAction("pending");
        }

        if (invoice.getProduct() == null || invoice.getProduct().isBlank()) {
            invoice.setProduct(safeItems.get(0).getName());
        }

        invoice.setVendor(customer.getName());
        invoice.setAmount((int) Math.round(totalAmount));

        return invoiceDao.save(invoice);
    }

    @Override
    public List<Invoice> getInvoices() {
        return invoiceDao.findAll();
    }

    @Override
    public Invoice deleteInvoice(long id) {
        Invoice invoice = invoiceDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        invoiceDao.delete(invoice);
        return invoice;
    }

    private Customer resolveCustomer(Invoice invoice) {
        if (invoice.getCustomer() == null || invoice.getCustomer().getId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is required for invoice");
        }

        return customerDao.findById(invoice.getCustomer().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected customer does not exist"));
    }
}
