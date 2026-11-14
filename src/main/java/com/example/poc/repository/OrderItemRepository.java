package com.example.poc.repository;

import com.example.poc.entity.OrderItem;
import com.example.poc.entity.OrderItem_;
import java.util.List;
import java.util.Objects;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.springframework.stereotype.Repository;

@Repository
public class OrderItemRepository {

    private final QueryDsl queryDsl;

    public OrderItemRepository(Config config) {
        this.queryDsl = new QueryDsl(config);
    }

    public List<OrderItem> findByOrderId(Long orderId) {
        // where は右辺が null の条件を除外するため、null を渡すと全件が対象になってしまう
        Objects.requireNonNull(orderId);
        OrderItem_ i = new OrderItem_();
        return queryDsl.from(i).where(w -> w.eq(i.orderId, orderId)).orderBy(o -> o.asc(i.id)).fetch();
    }

    public OrderItem insert(OrderItem orderItem) {
        OrderItem_ i = new OrderItem_();
        return queryDsl.insert(i).single(orderItem).returning().fetchOne();
    }
}
