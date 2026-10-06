package com.example.Used.Model.Requests;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ListingSearchRequests {
    private String keyword;          // Text to search in title
    private List<Long> categoryIds;  // List of category IDs to filter by (AND logic)
    private String sortBy;           // "price", "condition", or leave empty
    private String sortDir;          // "asc" or "desc"          // "asc" or "desc"
}
