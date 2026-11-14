package com.example.poc.entity;

import org.seasar.doma.Entity;
import org.seasar.doma.jdbc.entity.NamingType;

/** カテゴリ別売上の集計結果 (参照専用。テーブルとは対応しない)。 */
@Entity(immutable = true, naming = NamingType.SNAKE_LOWER_CASE)
public record CategorySales(
        String category,
        Long orderCount,
        Long totalQuantity,
        Long totalAmount) {
}
