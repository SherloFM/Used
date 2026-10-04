package com.example.Used.Model.Requests;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListingRequests {
    private String title;
    private String description;
    private double price;
    private String condition;
    private String location;
}
