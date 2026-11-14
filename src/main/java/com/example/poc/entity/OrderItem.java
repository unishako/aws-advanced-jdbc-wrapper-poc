package com.example.poc.entity;

import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity(immutable = true)
@Table(name = "order_item")
public record OrderItem(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id,
        Long orderId,
        Long productId,
        // 集計 (sum) の結果の型は引数の型で決まるため、オーバーフローしないよう Long にしている
        Long quantity,
        Long unitPrice) {
}
