package com.example.mobileproject.model;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SearchCriteria {
    private String location = "";
    private Set<String> amenityIds = new HashSet<>();
    private int guests = 1;
    private Date checkIn;
    private Date checkOut;

    public SearchCriteria() {
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Set<String> getAmenityIds() {
        return amenityIds;
    }

    public void setAmenityIds(Set<String> amenityIds) {
        this.amenityIds = amenityIds;
    }

    public int getGuests() {
        return guests;
    }

    public void setGuests(int guests) {
        this.guests = guests;
    }

    public Date getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(Date checkIn) {
        this.checkIn = checkIn;
    }

    public Date getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(Date checkOut) {
        this.checkOut = checkOut;
    }
}
