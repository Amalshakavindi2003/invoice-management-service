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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Set<String> VALID_STATUSES = Set.of("draft", "sent", "paid", "overdue", "cancelled");

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

        LocalDate invoiceDate = parseDateOrThrow(invoice.getDate(), "Invoice date must use YYYY-MM-DD format");

        String dueDateRaw = invoice.getDueDate();
        if (dueDateRaw == null || dueDateRaw.isBlank()) {
            dueDateRaw = invoiceDate.plusDays(14).toString();
        }
        LocalDate dueDate = parseDateOrThrow(dueDateRaw, "Due date must use YYYY-MM-DD format");

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

        String normalizedStatus = normalizeStatus(invoice.getAction());
        if (normalizedStatus == null) {
            normalizedStatus = "draft";
        }
        if ("sent".equals(normalizedStatus) && dueDate.isBefore(invoiceDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date cannot be before invoice date for sent invoices");
        }

        double paidAmount = Math.max(0, invoice.getPaidAmount());
        if (paidAmount > totalAmount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid amount cannot exceed total amount");
        }

        if (isFullyPaid(paidAmount, totalAmount)) {
            normalizedStatus = "paid";
            paidAmount = totalAmount;
            if (invoice.getPaidDate() == null || invoice.getPaidDate().isBlank()) {
                invoice.setPaidDate(LocalDate.now().toString());
            }
        }

        invoice.setCustomer(customer);
        invoice.setLineItems(safeItems);
        invoice.setSubtotal(subtotal);
        invoice.setTaxTotal(taxTotal);
        invoice.setDiscount(discount);
        invoice.setTotalAmount(totalAmount);
        invoice.setPaidAmount(paidAmount);
        invoice.setAction(applyOverdueIfNeeded(normalizedStatus, dueDate));
        invoice.setDate(invoiceDate.toString());
        invoice.setDueDate(dueDate.toString());

        if (invoice.getProduct() == null || invoice.getProduct().isBlank()) {
            invoice.setProduct(safeItems.get(0).getName());
        }

        invoice.setVendor(customer.getName());
        invoice.setAmount((int) Math.round(totalAmount));

        return invoiceDao.save(invoice);
    }

    @Override
    public List<Invoice> getInvoices() {
        List<Invoice> invoices = invoiceDao.findAll();
        boolean updated = false;

        for (Invoice invoice : invoices) {
            String normalizedStatus = normalizeStatus(invoice.getAction());
            if (normalizedStatus == null) {
                normalizedStatus = "draft";
                invoice.setAction(normalizedStatus);
                updated = true;
            }

            double total = resolveTotal(invoice);
            double paid = Math.max(0, invoice.getPaidAmount());
            if (paid > total) {
                invoice.setPaidAmount(total);
                paid = total;
                updated = true;
            }

            if (isFullyPaid(paid, total) && !"paid".equals(normalizedStatus)) {
                invoice.setAction("paid");
                if (invoice.getPaidDate() == null || invoice.getPaidDate().isBlank()) {
                    invoice.setPaidDate(LocalDate.now().toString());
                }
                updated = true;
                continue;
            }

            LocalDate dueDate = safeParseDate(invoice.getDueDate());
            if (dueDate != null) {
                String computed = applyOverdueIfNeeded(normalizedStatus, dueDate);
                if (!computed.equals(normalizedStatus)) {
                    invoice.setAction(computed);
                    updated = true;
                }
            }
        }

        if (updated) {
            invoiceDao.saveAll(invoices);
        }

        return invoices;
    }

    @Override
    public Invoice deleteInvoice(long id) {
        Invoice invoice = invoiceDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        invoiceDao.delete(invoice);
        return invoice;
    }

    @Override
    public Invoice updateInvoiceStatus(long id, String status) {
        Invoice invoice = invoiceDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be one of: Draft, Sent, Paid, Overdue, Cancelled");
        }

        double total = resolveTotal(invoice);
        double paid = Math.max(0, invoice.getPaidAmount());

        if ("paid".equals(normalizedStatus)) {
            invoice.setPaidAmount(total);
            if (invoice.getPaidDate() == null || invoice.getPaidDate().isBlank()) {
                invoice.setPaidDate(LocalDate.now().toString());
            }
        }

        if ("overdue".equals(normalizedStatus)) {
            LocalDate dueDate = safeParseDate(invoice.getDueDate());
            if (dueDate == null || !dueDate.isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice can be set to overdue only when due date is in the past");
            }
        }

        if ("sent".equals(normalizedStatus)) {
            if (isFullyPaid(paid, total)) {
                normalizedStatus = "paid";
            } else {
                LocalDate dueDate = safeParseDate(invoice.getDueDate());
                if (dueDate != null) {
                    normalizedStatus = applyOverdueIfNeeded(normalizedStatus, dueDate);
                }
            }
        }

        invoice.setAction(normalizedStatus);
        return invoiceDao.save(invoice);
    }

    @Override
    public String sendReminder(long id) {
        Invoice invoice = invoiceDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        String status = normalizeStatus(invoice.getAction());
        if (status == null) {
            status = "draft";
        }

        if ("paid".equals(status) || "cancelled".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder cannot be sent for paid or cancelled invoices");
        }

        Customer customer = invoice.getCustomer();
        String customerEmail = customer == null ? "unknown@customer" : customer.getEmail();
        invoice.setReminderSentAt(LocalDateTime.now().toString());
        invoiceDao.save(invoice);

        return "Mock reminder sent to " + customerEmail + " for invoice #" + invoice.getId();
    }

    @Override
    public Invoice recordPayment(long id, double amount, String paidDate, String paymentMethod, String paymentReference, String paymentNotes) {
        Invoice invoice = invoiceDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        String normalizedStatus = normalizeStatus(invoice.getAction());
        if (normalizedStatus == null) {
            normalizedStatus = "draft";
        }

        if ("cancelled".equals(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot record payment for cancelled invoice");
        }

        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero");
        }

        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }

        LocalDate paymentDate = paidDate == null || paidDate.isBlank()
                ? LocalDate.now()
                : parseDateOrThrow(paidDate, "Paid date must use YYYY-MM-DD format");

        double total = resolveTotal(invoice);
        double currentPaid = Math.max(0, invoice.getPaidAmount());
        double remaining = Math.max(0, total - currentPaid);

        if (amount - remaining > 0.000001) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount cannot exceed remaining balance");
        }

        double newPaid = currentPaid + amount;
        invoice.setPaidAmount(newPaid);
        invoice.setPaidDate(paymentDate.toString());
        invoice.setPaymentMethod(paymentMethod.trim());
        invoice.setPaymentReference(paymentReference == null ? null : paymentReference.trim());
        invoice.setPaymentNotes(paymentNotes == null ? null : paymentNotes.trim());

        if (isFullyPaid(newPaid, total)) {
            invoice.setPaidAmount(total);
            invoice.setAction("paid");
        } else {
            LocalDate dueDate = safeParseDate(invoice.getDueDate());
            String nextStatus = "sent";
            if (dueDate != null) {
                nextStatus = applyOverdueIfNeeded(nextStatus, dueDate);
            }
            invoice.setAction(nextStatus);
        }

        return invoiceDao.save(invoice);
    }

    private Customer resolveCustomer(Invoice invoice) {
        if (invoice.getCustomer() == null || invoice.getCustomer().getId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is required for invoice");
        }

        return customerDao.findById(invoice.getCustomer().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected customer does not exist"));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if ("pending".equals(normalized)) {
            normalized = "sent";
        }

        return VALID_STATUSES.contains(normalized) ? normalized : null;
    }

    private LocalDate parseDateOrThrow(String value, String errorMessage) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }

    private LocalDate safeParseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String applyOverdueIfNeeded(String currentStatus, LocalDate dueDate) {
        if ("sent".equals(currentStatus) && dueDate.isBefore(LocalDate.now())) {
            return "overdue";
        }

        return currentStatus;
    }

    private boolean isFullyPaid(double paid, double total) {
        return paid + 0.000001 >= total;
    }

    private double resolveTotal(Invoice invoice) {
        double total = invoice.getTotalAmount();
        if (total <= 0) {
            total = invoice.getAmount();
        }
        return Math.max(0, total);
    }
}