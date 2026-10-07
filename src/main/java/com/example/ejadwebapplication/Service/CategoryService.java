package com.example.ejadwebapplication.Service;

import com.example.ejadwebapplication.Api.ApiException;
import com.example.ejadwebapplication.DTOIN.CategoryDTOIn;
import com.example.ejadwebapplication.Enums.ReportStatus;
import com.example.ejadwebapplication.Model.Category;
import com.example.ejadwebapplication.Model.Report;
import com.example.ejadwebapplication.Repository.CategoryRepository;
import com.example.ejadwebapplication.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ReportRepository reportRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Integer id) {
        return findCategory(id);
    }

    public void addCategory(CategoryDTOIn dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new ApiException("Category name already exists");
        }
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        categoryRepository.save(category);
    }

    public void updateCategory(Integer id, CategoryDTOIn dto) {
        Category category = findCategory(id);
        if (!category.getName().equalsIgnoreCase(dto.getName())
                && categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new ApiException("Category name already exists");
        }
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        categoryRepository.save(category);
    }

    public void deleteCategory(Integer id) {
        Category category = findCategory(id);
        if (reportRepository.existsByCategory(category)) {
            throw new ApiException("Cannot delete a category that has reports");
        }
        categoryRepository.delete(category);
    }

    // كم بلاغ في كل تصنيف (أكثر الأغراض ضياعاً)
    public List<Map<String, Object>> getCategoryStatistics() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category category : categoryRepository.findAll()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("categoryId", category.getId());
            stats.put("categoryName", category.getName());
            stats.put("totalReports", reportRepository.countByCategory(category));
            stats.put("openReports", reportRepository.countByCategoryAndStatus(category, ReportStatus.OPEN));
            result.add(stats);
        }
        return result;
    }

    public Integer getReportCountByCategory(Integer categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(() -> new ApiException("Category not found with provided id"));
        return category.getReports().size();
    }


    private Category findCategory(Integer id) {
        Category category = categoryRepository.findCategoryById(id);
        if (category == null) {
            throw new ApiException("Category not found");
        }
        return category;
    }
}