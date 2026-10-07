package com.example.ejadwebapplication.Controller;

import com.example.ejadwebapplication.Api.ApiResponse;
import com.example.ejadwebapplication.DTOIN.CategoryDTOIn;
import com.example.ejadwebapplication.Service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllCategories() {
        return ResponseEntity.status(200)
                .body(categoryService.getAllCategories());
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Integer id) {
        return ResponseEntity.status(200)
                .body(categoryService.getCategoryById(id));
    }

    @PostMapping("/add")
    public ResponseEntity<?> addCategory(@RequestBody @Valid CategoryDTOIn dto) {
        categoryService.addCategory(dto);
        return ResponseEntity.status(200)
                .body(new ApiResponse("Category added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Integer id,
            @RequestBody @Valid CategoryDTOIn dto) {

        categoryService.updateCategory(id, dto);
        return ResponseEntity.status(200)
                .body(new ApiResponse("Category updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.status(200)
                .body(new ApiResponse("Category deleted successfully"));
    }


    @GetMapping("/statistics")
    public ResponseEntity<?> getCategoryStatistics() {
        return ResponseEntity.status(200).body(categoryService.getCategoryStatistics());
    }

    @GetMapping("/{categoryId}/report-count")
    public ResponseEntity<?> getReportCountByCategory(@PathVariable Integer categoryId) {
        return ResponseEntity.status(200)
                .body(categoryService.getReportCountByCategory(categoryId));
    }
}