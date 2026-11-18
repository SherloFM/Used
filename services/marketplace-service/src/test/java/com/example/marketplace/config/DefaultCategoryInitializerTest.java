package com.example.marketplace.config;

import com.example.marketplace.model.Categories;
import com.example.marketplace.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCategoryInitializerTest {

    @Test
    void addsTheFullCatalogueWhenTheDatabaseIsEmpty() throws Exception {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        DefaultCategoryInitializer initializer = new DefaultCategoryInitializer(repository);

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Categories> saved = ArgumentCaptor.forClass(Categories.class);
        verify(repository, org.mockito.Mockito.times(
                DefaultCategoryInitializer.DEFAULT_CATEGORIES.size())).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(c -> "Cars".equals(c.getName())));
        assertTrue(saved.getAllValues().stream().anyMatch(c -> "Real Estate".equals(c.getName())));
        assertEquals(50, DefaultCategoryInitializer.DEFAULT_CATEGORIES.size());
    }

    @Test
    void neverOverwritesAnExistingCategory() throws Exception {
        CategoryRepository repository = mock(CategoryRepository.class);
        Categories existing = new Categories();
        existing.setName("Books");
        when(repository.findByNameIgnoreCase(any())).thenReturn(Optional.of(existing));
        DefaultCategoryInitializer initializer = new DefaultCategoryInitializer(repository);

        initializer.run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
    }
}
