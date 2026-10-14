
package com.example.mobileproject.model;

import java.util.Date;

public class Payment {

    private String paymentId;
    private String exchangeId;
    private double amount;
    private String provider;
    private String transactionCode;
    private PaymentStatus status;
    private Date paidAt;

    public Payment(String paymentId, String exchangeId, double amount,
                   String provider, String transactionCode,
                   PaymentStatus status, Date paidAt) {
        this.paymentId = paymentId;
        this.exchangeId = exchangeId;
        this.amount = amount;
        this.provider = provider;
        this.transactionCode = transactionCode;
        this.status = status;
        this.paidAt = paidAt;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Date getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Date paidAt) {
        this.paidAt = paidAt;
    }

    public void createPayment() {
        this.status = PaymentStatus.PENDING;
    }

    public void markPaid(String transactionCode) {
        this.transactionCode = transactionCode;
        this.status = PaymentStatus.PAID;
        this.paidAt = new Date();
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
}
