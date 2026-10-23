package io.clientra.controller;

import io.clientra.entity.Product;
import io.clientra.exception.BusinessRuleException;
import io.clientra.exception.ResourceNotFoundException;
import io.clientra.repository.ProductRepository;
import io.clientra.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Product catalogue. Reads: Admin + Sales (for orders). Writes: Admin only (enforced in SecurityConfig). */
@Tag(name = "Products")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository repo;
    private final AuditService audit;

    public record ProductDto(Long id, String sku, String name, String category, BigDecimal unitPrice, BigDecimal gstRate,
                             boolean active) {
        static ProductDto from(Product p) {
            return new ProductDto(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getUnitPrice(), p.getGstRate(), p.isActive());
        }
    }

    public record ProductRequest(
            @NotBlank(message = "SKU is required") @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Letters, numbers and dashes only") String sku,
            @NotBlank(message = "Name is required") @Size(max = 150) String name,
            @Size(max = 80) String category,
            @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be positive") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin("0") @DecimalMax(value = "28", message = "GST rate cannot exceed 28%") BigDecimal gstRate) {
    }

    public record ActiveRequest(boolean active) {
    }

    @GetMapping
    public List<ProductDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return (activeOnly ? repo.findByActiveTrueOrderByName() : repo.findAllByOrderByName()).stream().map(ProductDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProductDto create(@Valid @RequestBody ProductRequest req) {
        if (repo.existsBySkuIgnoreCase(req.sku().trim())) throw new BusinessRuleException("SKU already exists");
        Product p = new Product();
        apply(p, req);
        p = repo.save(p);
        audit.log("PRODUCT", p.getId(), null, "CREATED", "Product " + p.getName() + " added", Map.of("sku", p.getSku()));
        return ProductDto.from(p);
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        Product p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!p.getSku().equalsIgnoreCase(req.sku().trim()) && repo.existsBySkuIgnoreCase(req.sku().trim())) {
            throw new BusinessRuleException("SKU already exists");
        }
        BigDecimal oldPrice = p.getUnitPrice();
        apply(p, req);
        audit.log("PRODUCT", id, null, "UPDATED", "Product " + p.getName() + " updated",
                Map.of("oldPrice", oldPrice, "newPrice", p.getUnitPrice()));
        return ProductDto.from(p);
    }

    @PatchMapping("/{id}/active")
    @Transactional
    public ProductDto setActive(@PathVariable Long id, @RequestBody ActiveRequest req) {
        Product p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        p.setActive(req.active());
        audit.log("PRODUCT", id, null, req.active() ? "ACTIVATED" : "DEACTIVATED",
                "Product " + p.getName() + (req.active() ? " activated" : " deactivated"));
        return ProductDto.from(p);
    }

    private static void apply(Product p, ProductRequest req) {
        p.setSku(req.sku().trim().toUpperCase());
        p.setName(req.name().trim());
        p.setCategory(req.category() == null || req.category().isBlank() ? null : req.category().trim());
        p.setUnitPrice(req.unitPrice());
        p.setGstRate(req.gstRate());
    }
}
