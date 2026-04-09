package com.invoiceprocessing.server.services;

import com.invoiceprocessing.server.model.Invoice;

import java.util.List;

public interface InvoiceService {
    Invoice addInvoice(Invoice invoice);

    List<Invoice> getInvoices();

    Invoice deleteInvoice(long id);

    Invoice updateInvoiceStatus(long id, String status);

    String sendReminder(long id);

    Invoice recordPayment(long id, double amount, String paidDate, String paymentMethod, String paymentReference, String paymentNotes);
}