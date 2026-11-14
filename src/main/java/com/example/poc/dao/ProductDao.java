package com.example.poc.dao;

import com.example.poc.entity.Product;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

@Dao
@ConfigAutowireable
public interface ProductDao {

    @Sql("""
            select /*%expand*/* from product where id = /* id */0
            """)
    @Select
    Optional<Product> selectById(Long id);

    @Sql("""
            select /*%expand*/* from product
            where
            /*%if category != null */
                category = /* category */'食品'
            /*%end*/
            order by id
            limit /* limit */10 offset /* offset */0
            """)
    @Select
    List<Product> selectPage(String category, int limit, int offset);

    @Insert
    Result<Product> insert(Product product);

    /** 在庫を増減する。在庫が不足する場合は更新されず 0 を返す。 */
    @Sql("""
            update product
            set stock = stock + /* delta */0
            where id = /* id */0
              and stock + /* delta */0 >= 0
            """)
    @Update
    int addStock(Long id, int delta);
}
