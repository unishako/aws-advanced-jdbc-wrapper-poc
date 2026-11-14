package com.example.poc.dao;

import com.example.poc.entity.Customer;
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
public interface CustomerDao {

    @Sql("""
            select /*%expand*/* from customer where id = /* id */0
            """)
    @Select
    Optional<Customer> selectById(Long id);

    @Sql("""
            select /*%expand*/* from customer
            order by id
            limit /* limit */10 offset /* offset */0
            """)
    @Select
    List<Customer> selectPage(int limit, int offset);

    @Sql("""
            select count(*) from customer
            """)
    @Select
    long count();

    @Insert
    Result<Customer> insert(Customer customer);
}
