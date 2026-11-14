package com.example.poc.repository;

import static org.seasar.doma.jdbc.criteria.expression.Expressions.countDistinct;
import static org.seasar.doma.jdbc.criteria.expression.Expressions.mul;
import static org.seasar.doma.jdbc.criteria.expression.Expressions.sum;

import com.example.poc.entity.CategorySales;
import com.example.poc.entity.Order;
import com.example.poc.entity.OrderItem_;
import com.example.poc.entity.Order_;
import com.example.poc.entity.Product_;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final QueryDsl queryDsl;

    public OrderRepository(Config config) {
        this.queryDsl = new QueryDsl(config);
    }

    public Optional<Order> findById(Long id) {
        // where は右辺が null の条件を除外するため、null を渡すと全件が対象になってしまう
        Objects.requireNonNull(id);
        Order_ o = new Order_();
        return queryDsl.from(o).where(w -> w.eq(o.id, id)).fetchOptional();
    }

    public List<Order> findByCustomerId(Long customerId, int limit) {
        Objects.requireNonNull(customerId);
        Order_ o = new Order_();
        return queryDsl.from(o)
                .where(w -> w.eq(o.customerId, customerId))
                .orderBy(ob -> ob.desc(o.orderedAt))
                .limit(limit)
                .fetch();
    }

    /** カテゴリ別の売上集計 (結合と集計を伴う、読み取り専用の重めのクエリ)。売上の降順で返す。 */
    public List<CategorySales> summarizeByCategory() {
        Order_ o = new Order_();
        OrderItem_ i = new OrderItem_();
        Product_ p = new Product_();
        return queryDsl.from(o)
                .innerJoin(i, on -> on.eq(o.id, i.orderId))
                .innerJoin(p, on -> on.eq(i.productId, p.id))
                .where(w -> w.ne(o.status, "CANCELLED"))
                .groupBy(p.category)
                .select(p.category, countDistinct(o.id), sum(i.quantity), sum(mul(i.quantity, i.unitPrice)))
                .fetch()
                .stream()
                .map(t -> new CategorySales(t.getItem1(), t.getItem2(), t.getItem3(), t.getItem4()))
                .sorted(Comparator.comparing(CategorySales::totalAmount).reversed())
                .toList();
    }

    /** 挿入した行を、DB のデフォルト値(ordered_at など)を含めて返す。 */
    public Order insert(Order order) {
        Order_ o = new Order_();
        return queryDsl.insert(o).single(order).returning().fetchOne();
    }
}
