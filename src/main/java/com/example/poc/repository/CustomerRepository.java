package com.example.poc.repository;

import com.example.poc.entity.Customer;
import com.example.poc.entity.Customer_;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.seasar.doma.jdbc.criteria.expression.Expressions;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final QueryDsl queryDsl;

    public CustomerRepository(Config config) {
        this.queryDsl = new QueryDsl(config);
    }

    public Optional<Customer> findById(Long id) {
        // where は右辺が null の条件を除外するため、null を渡すと全件が対象になってしまう
        Objects.requireNonNull(id);
        Customer_ c = new Customer_();
        return queryDsl.from(c).where(w -> w.eq(c.id, id)).fetchOptional();
    }

    public List<Customer> findAll(int limit, int offset) {
        Customer_ c = new Customer_();
        return queryDsl.from(c).orderBy(o -> o.asc(c.id)).limit(limit).offset(offset).fetch();
    }

    public long count() {
        Customer_ c = new Customer_();
        // このクラスの count() と区別するため、Expressions.count() と明示する
        return queryDsl.from(c).select(Expressions.count()).fetchOne();
    }

    /** 挿入した行を、DB のデフォルト値(created_at など)を含めて返す。 */
    public Customer insert(Customer customer) {
        Customer_ c = new Customer_();
        return queryDsl.insert(c).single(customer).returning().fetchOne();
    }
}
