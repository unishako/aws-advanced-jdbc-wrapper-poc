package com.example.poc.entity;

/** カテゴリ別売上の集計結果 (Doma のエンティティではなく、集計結果を詰め替えるための型)。 */
public record CategorySales(
        String category,
        Long orderCount,
        Long totalQuantity,
        Long totalAmount) {
}
