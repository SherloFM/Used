package com.example.marketplace.service;

import com.example.marketplace.exceptions.InformationExistException;
import com.example.marketplace.exceptions.ResourceNotFoundException;
import com.example.marketplace.model.Categories;
import com.example.marketplace.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Categories createCategory(Categories categoryRequest) {

        if (categoryRepository
                .findByNameIgnoreCase(categoryRequest.getName())
                .isPresent()) {

            throw new InformationExistException(
                    "Category already exists"
            );
        }

        Categories category = new Categories();
        category.setName(categoryRequest.getName());
        category.setDescription(categoryRequest.getDescription());

        return categoryRepository.save(category);
    }

    public List<Categories> getCategories() {
        return categoryRepository.findAll();
    }

    public void deleteCategory(Long id) {

        Categories category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );

        categoryRepository.delete(category);
    }

    public Categories getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found"
                        )
                );
    }
}
