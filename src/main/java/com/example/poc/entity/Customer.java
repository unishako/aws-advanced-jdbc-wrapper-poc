package com.example.poc.entity;

import java.time.LocalDateTime;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.jdbc.entity.NamingType;

@Entity(immutable = true, naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "customer")
public record Customer(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id,
        String name,
        String email,
        String prefecture,
        @Column(insertable = false, updatable = false) LocalDateTime createdAt) {
}
