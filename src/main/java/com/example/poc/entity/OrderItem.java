package com.example.poc.entity;

import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.jdbc.entity.NamingType;

@Entity(immutable = true, naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "order_item")
public record OrderItem(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id,
        Long orderId,
        Long productId,
        Integer quantity,
        Integer unitPrice) {
}
