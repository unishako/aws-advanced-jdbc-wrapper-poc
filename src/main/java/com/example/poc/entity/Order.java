package com.example.poc.entity;

import java.time.LocalDateTime;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.jdbc.entity.NamingType;

/** 注文 (テーブル名は予約語を避けて orders)。 */
@Entity(immutable = true, naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "orders")
public record Order(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id,
        Long customerId,
        String status,
        Integer total,
        @Column(insertable = false, updatable = false) LocalDateTime orderedAt) {
}
