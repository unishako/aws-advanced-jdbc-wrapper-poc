package com.example.poc.controller;

import com.example.poc.entity.CategorySales;
import com.example.poc.service.OrderService;
import com.example.poc.service.OrderService.Line;
import com.example.poc.service.OrderService.OrderDetail;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record PlaceRequest(Long customerId, List<Line> items) {
    }

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    /** カテゴリ別売上 (読み取り専用の集計。Reader で実行される)。 */
    @GetMapping("/summary")
    public List<CategorySales> summary() {
        return service.summarizeByCategory();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail place(@RequestBody PlaceRequest request) {
        return service.place(request.customerId(), request.items());
    }
}
