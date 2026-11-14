package com.example.poc.service;

import com.example.poc.repository.CustomerRepository;
import com.example.poc.repository.OrderRepository;
import com.example.poc.repository.OrderItemRepository;
import com.example.poc.repository.ProductRepository;
import com.example.poc.entity.CategorySales;
import com.example.poc.entity.Order;
import com.example.poc.entity.OrderItem;
import com.example.poc.entity.Product;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    public record Line(Long productId, int quantity) {
    }

    public record OrderDetail(Order order, List<OrderItem> items) {
    }

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(CustomerRepository customerRepository, ProductRepository productRepository, OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "注文が存在しません: " + id));
        return new OrderDetail(order, orderItemRepository.findByOrderId(id));
    }

    @Transactional(readOnly = true)
    public List<Order> listByCustomer(Long customerId, int limit) {
        return orderRepository.findByCustomerId(customerId, limit);
    }

    @Transactional(readOnly = true)
    public List<CategorySales> summarizeByCategory() {
        return orderRepository.summarizeByCategory();
    }

    /** 注文を登録する (在庫引当 → 注文 → 明細)。1トランザクションで Writer に対して実行される。 */
    @Transactional
    public OrderDetail place(Long customerId, List<Line> lines) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "顧客が存在しません: " + customerId));

        List<OrderItem> items = new ArrayList<>();
        int total = 0;
        for (Line line : lines) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "商品が存在しません: " + line.productId()));
            if (productRepository.addStock(product.id(), -line.quantity()) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "在庫が不足しています: " + product.id());
            }
            items.add(new OrderItem(null, null, product.id(), (long) line.quantity(), product.price().longValue()));
            total += product.price() * line.quantity();
        }

        Order order = orderRepository.insert(new Order(null, customerId, "NEW", total, null));
        List<OrderItem> saved = items.stream()
                .map(i -> orderItemRepository
                        .insert(new OrderItem(null, order.id(), i.productId(), i.quantity(), i.unitPrice()))
                        )
                .toList();
        return new OrderDetail(order, saved);
    }
}
