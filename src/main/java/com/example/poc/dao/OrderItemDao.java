package com.example.poc.dao;

import com.example.poc.entity.OrderItem;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

@Dao
@ConfigAutowireable
public interface OrderItemDao {

    @Sql("""
            select /*%expand*/* from order_item where order_id = /* orderId */0 order by id
            """)
    @Select
    List<OrderItem> selectByOrderId(Long orderId);

    @Insert
    Result<OrderItem> insert(OrderItem orderItem);
}
