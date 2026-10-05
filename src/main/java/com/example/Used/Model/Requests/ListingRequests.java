package com.example.Used.Model.Requests;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListingRequests {
    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    @Positive
    private double price;

    @NotBlank
    private String condition;

    @Size(max = 100)
    private String location;
}
