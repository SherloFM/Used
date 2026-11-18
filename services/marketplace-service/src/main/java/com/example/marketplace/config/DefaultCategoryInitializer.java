package com.example.marketplace.config;

import com.example.marketplace.model.Categories;
import com.example.marketplace.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Maintains the marketplace's reusable category catalogue. This is data
 * initialization only: it adds missing names and never updates or deletes a
 * category, so existing listing links remain untouched across deployments.
 */
@Component
public class DefaultCategoryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCategoryInitializer.class);

    static final List<String> DEFAULT_CATEGORIES = List.of(
            "Accessories", "Antiques & Collectibles", "Art & Craft Supplies",
            "Audio & Headphones", "Baby & Kids", "Bags & Luggage", "Beauty & Skincare",
            "Bicycles", "Books", "Cameras & Photography", "Cars", "Clothing",
            "Coins & Stamps", "Computers & Laptops", "Construction Materials",
            "Consumer Electronics", "DIY & Hardware", "Educational Supplies",
            "Fitness & Exercise", "Food & Beverages", "Footwear", "Furniture",
            "Gaming & Consoles", "Garden & Outdoor", "Health & Wellness",
            "Home Appliances", "Home Decor", "Jewelry & Watches", "Kitchen & Dining",
            "Mobile Phones & Tablets", "Motorcycles", "Movies & Music",
            "Musical Instruments", "Office Equipment", "Other", "Party Supplies",
            "Pet Supplies", "Real Estate", "Services", "Smart Home",
            "Software", "Sports & Outdoors", "Stationery", "Tickets & Events",
            "Tools & Machinery", "Toys & Games", "Travel & Camping",
            "Vehicle Parts & Accessories", "Video Games", "Vintage Items"
    );

    private final CategoryRepository categories;

    public DefaultCategoryInitializer(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        for (String name : DEFAULT_CATEGORIES) {
            if (categories.findByNameIgnoreCase(name).isEmpty()) {
                Categories category = new Categories();
                category.setName(name);
                category.setDescription("Marketplace category: " + name);
                categories.save(category);
                added++;
            }
        }
        if (added > 0) {
            log.info("Added {} missing default marketplace categories", added);
        }
    }
}
