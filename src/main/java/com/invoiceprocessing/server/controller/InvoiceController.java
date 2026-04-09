package com.invoiceprocessing.server.controller;

import com.invoiceprocessing.server.model.Invoice;
import com.invoiceprocessing.server.services.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class InvoiceController {

    @Autowired
    InvoiceService invoiceService;

    @PostMapping("/invoice")
    public Invoice addInvoice(@RequestBody Invoice invoice){
        return this.invoiceService.addInvoice(invoice);
    }

    @GetMapping("/invoice")
    public List<Invoice> getInvoices() {
        return this.invoiceService.getInvoices();
    }

    @DeleteMapping("/invoice/{invoiceId}")
    public Invoice deleteInvoice(@PathVariable String invoiceId){
      return this.invoiceService.deleteInvoice(Long.parseLong(invoiceId));
    }

    @PutMapping("/invoice/{invoiceId}/status")
    public Invoice updateStatus(@PathVariable String invoiceId, @RequestBody Map<String, String> payload) {
        String status = payload == null ? null : payload.get("status");
        return this.invoiceService.updateInvoiceStatus(Long.parseLong(invoiceId), status);
    }

    @PostMapping("/invoice/{invoiceId}/reminder")
    public Map<String, String> sendReminder(@PathVariable String invoiceId) {
        String message = this.invoiceService.sendReminder(Long.parseLong(invoiceId));
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", message);
        return response;
    }

    @PostMapping("/invoice/{invoiceId}/payment")
    public Invoice recordPayment(@PathVariable String invoiceId, @RequestBody Map<String, Object> payload) {
        double amount = toDouble(payload == null ? null : payload.get("amount"));
        String paidDate = toString(payload == null ? null : payload.get("paidDate"));
        String paymentMethod = toString(payload == null ? null : payload.get("paymentMethod"));
        String paymentReference = toString(payload == null ? null : payload.get("paymentReference"));
        String paymentNotes = toString(payload == null ? null : payload.get("paymentNotes"));

        return this.invoiceService.recordPayment(
                Long.parseLong(invoiceId),
                amount,
                paidDate,
                paymentMethod,
                paymentReference,
                paymentNotes
        );
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}