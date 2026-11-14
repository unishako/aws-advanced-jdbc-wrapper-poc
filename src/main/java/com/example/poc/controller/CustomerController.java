package com.example.poc.controller;

import com.example.poc.entity.Customer;
import com.example.poc.entity.Order;
import com.example.poc.service.CustomerService;
import com.example.poc.service.OrderService;
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
@RequestMapping("/api/customers")
public class CustomerController {

    public record CreateRequest(String name, String email, String prefecture) {
    }

    private final CustomerService customerService;
    private final OrderService orderService;

    public CustomerController(CustomerService customerService, OrderService orderService) {
        this.customerService = customerService;
        this.orderService = orderService;
    }

    @GetMapping
    public List<Customer> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return customerService.list(limit, offset);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/orders")
    public List<Order> orders(@PathVariable Long id, @RequestParam(defaultValue = "10") int limit) {
        return orderService.listByCustomer(id, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@RequestBody CreateRequest request) {
        return customerService.create(request.name(), request.email(), request.prefecture());
    }
}
