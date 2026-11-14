package com.example.poc.dao;

import com.example.poc.entity.CategorySales;
import com.example.poc.entity.Order;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

@Dao
@ConfigAutowireable
public interface OrderDao {

    @Sql("""
            select /*%expand*/* from orders where id = /* id */0
            """)
    @Select
    Optional<Order> selectById(Long id);

    @Sql("""
            select /*%expand*/* from orders
            where customer_id = /* customerId */0
            order by ordered_at desc
            limit /* limit */10
            """)
    @Select
    List<Order> selectByCustomerId(Long customerId, int limit);

    /** カテゴリ別の売上集計 (結合と集計を伴う、読み取り専用の重めのクエリ)。 */
    @Sql("""
            select p.category as category,
                   count(distinct o.id) as order_count,
                   sum(i.quantity) as total_quantity,
                   sum(i.quantity * i.unit_price) as total_amount
            from orders o
            join order_item i on i.order_id = o.id
            join product p on p.id = i.product_id
            where o.status <> 'CANCELLED'
            group by p.category
            order by total_amount desc
            """)
    @Select
    List<CategorySales> summarizeByCategory();

    @Insert
    Result<Order> insert(Order order);
}
