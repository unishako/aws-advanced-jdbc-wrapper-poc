package com.example.poc.repository;

import static org.seasar.doma.jdbc.criteria.expression.Expressions.add;

import com.example.poc.entity.Product;
import com.example.poc.entity.Product_;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.criteria.QueryDsl;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final QueryDsl queryDsl;

    public ProductRepository(Config config) {
        this.queryDsl = new QueryDsl(config);
    }

    public Optional<Product> findById(Long id) {
        // where は右辺が null の条件を除外するため、null を渡すと全件が対象になってしまう
        Objects.requireNonNull(id);
        Product_ p = new Product_();
        return queryDsl.from(p).where(w -> w.eq(p.id, id)).fetchOptional();
    }

    /** category が null のときは、カテゴリで絞り込まない (where は右辺が null の条件を除外する)。 */
    public List<Product> findAll(String category, int limit, int offset) {
        Product_ p = new Product_();
        return queryDsl.from(p)
                .where(w -> w.eq(p.category, category))
                .orderBy(o -> o.asc(p.id))
                .limit(limit)
                .offset(offset)
                .fetch();
    }

    /** 挿入した行を、DB のデフォルト値(created_at など)を含めて返す。 */
    public Product insert(Product product) {
        Product_ p = new Product_();
        return queryDsl.insert(p).single(product).returning().fetchOne();
    }

    /** 在庫を増減する。在庫が不足する場合 (stock + delta が 0 未満) は更新されず 0 を返す。 */
    public int addStock(Long id, int delta) {
        Objects.requireNonNull(id);
        Product_ p = new Product_();
        return queryDsl.update(p)
                .set(s -> s.value(p.stock, add(p.stock, delta)))
                .where(w -> {
                    w.eq(p.id, id);
                    w.ge(p.stock, -delta);
                })
                .execute();
    }
}
