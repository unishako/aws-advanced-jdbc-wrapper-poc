package com.example.poc.service;

import com.example.poc.dao.CustomerDao;
import com.example.poc.dao.OrderDao;
import com.example.poc.dao.OrderItemDao;
import com.example.poc.dao.ProductDao;
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

    private final CustomerDao customerDao;
    private final ProductDao productDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;

    public OrderService(CustomerDao customerDao, ProductDao productDao, OrderDao orderDao, OrderItemDao orderItemDao) {
        this.customerDao = customerDao;
        this.productDao = productDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        Order order = orderDao.selectById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "注文が存在しません: " + id));
        return new OrderDetail(order, orderItemDao.selectByOrderId(id));
    }

    @Transactional(readOnly = true)
    public List<Order> listByCustomer(Long customerId, int limit) {
        return orderDao.selectByCustomerId(customerId, limit);
    }

    @Transactional(readOnly = true)
    public List<CategorySales> summarizeByCategory() {
        return orderDao.summarizeByCategory();
    }

    /** 注文を登録する (在庫引当 → 注文 → 明細)。1トランザクションで Writer に対して実行される。 */
    @Transactional
    public OrderDetail place(Long customerId, List<Line> lines) {
        customerDao.selectById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "顧客が存在しません: " + customerId));

        List<OrderItem> items = new ArrayList<>();
        int total = 0;
        for (Line line : lines) {
            Product product = productDao.selectById(line.productId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "商品が存在しません: " + line.productId()));
            if (productDao.addStock(product.id(), -line.quantity()) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "在庫が不足しています: " + product.id());
            }
            items.add(new OrderItem(null, null, product.id(), line.quantity(), product.price()));
            total += product.price() * line.quantity();
        }

        Order order = orderDao.insert(new Order(null, customerId, "NEW", total, null)).getEntity();
        List<OrderItem> saved = items.stream()
                .map(i -> orderItemDao
                        .insert(new OrderItem(null, order.id(), i.productId(), i.quantity(), i.unitPrice()))
                        .getEntity())
                .toList();
        return new OrderDetail(order, saved);
    }
}
