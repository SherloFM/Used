package com.example.Used.Controller;

import com.example.Used.Model.Categories;
import com.example.Used.Service.CategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Categories createCategory(
            @RequestBody Categories category
    ) {
        return categoryService.createCategory(category);
    }

    @GetMapping
    public List<Categories> getCategories() {
        return categoryService.getCategories();
    }

}
