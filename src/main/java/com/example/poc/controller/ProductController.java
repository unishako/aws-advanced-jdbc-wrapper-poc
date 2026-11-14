package com.example.poc.controller;

import com.example.poc.entity.Product;
import com.example.poc.service.ProductService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    public record CreateRequest(String name, String category, int price, int stock) {
    }

    public record StockRequest(int delta) {
    }

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<Product> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.list(category, limit, offset);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody CreateRequest request) {
        return service.create(request.name(), request.category(), request.price(), request.stock());
    }

    @PostMapping("/{id}/stock")
    public Product addStock(@PathVariable Long id, @RequestBody StockRequest request) {
        return service.addStock(id, request.delta());
    }
}
