package com.example.mobileproject.model;

import java.util.Date;

public class Exchange {

    private String exchangeId;
    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public ExchangeType getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public int getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(int guestCount) {
        this.guestCount = guestCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public ExchangeStatus getStatus() {
        return status;
    }

    public void setStatus(ExchangeStatus status) {
        this.status = status;
    }

    private String requesterId;
    private String hostId;
    private String postId;
    private ExchangeType exchangeType;
    private Date startDate;
    private Date endDate;
    private int guestCount;
    private String note;
    private String cancelReason;
    private ExchangeStatus status;

    public Exchange(String exchangeId, String requesterId, String hostId, String postId, ExchangeType exchangeType, Date startDate, Date endDate, int guestCount, String note, String cancelReason, ExchangeStatus status) {
        this.exchangeId = exchangeId;
        this.requesterId = requesterId;
        this.hostId = hostId;
        this.postId = postId;
        this.exchangeType = exchangeType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.guestCount = guestCount;
        this.note = note;
        this.cancelReason = cancelReason;
        this.status = status;
    }
    public void request(){
        this.status = ExchangeStatus.PENDING;
    }
    public void accept(){
        this.status = ExchangeStatus.ACCEPTED;
    }
    public void reject(){
        this.status = ExchangeStatus.REJECTED;
    }
    public void cancel(String byUserId, String reason){
        if (byUserId.equals(requesterId) || byUserId.equals(hostId)) {
            this.status = ExchangeStatus.CANCELLED;
            this.cancelReason = reason;
        } else {
            throw new IllegalArgumentException(
                    "INVALID"
            );
        }
    }
    public void markOngoing(){
        this.status = ExchangeStatus.ONGOING;
    }
    public void complete(){
        this.status = ExchangeStatus.COMPLETED;
    }
}