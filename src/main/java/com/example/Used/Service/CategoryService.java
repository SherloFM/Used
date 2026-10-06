package com.example.Used.Service;

import com.example.Used.Exceptions.InformationExistException;
import com.example.Used.Model.Categories;
import com.example.Used.Repository.CategoryRepository;
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
}

