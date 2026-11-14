package com.example.poc.service;

import com.example.poc.dao.ProductDao;
import com.example.poc.entity.Product;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductDao productDao;

    public ProductService(ProductDao productDao) {
        this.productDao = productDao;
    }

    /** 読み取り専用トランザクション: setReadOnly(true) により Reader へ振り分けられる。 */
    @Transactional(readOnly = true)
    public List<Product> list(String category, int limit, int offset) {
        return productDao.selectPage(category, limit, offset);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return productDao.selectById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品が存在しません: " + id));
    }

    /** 書き込みトランザクション: Writer で実行される。 */
    @Transactional
    public Product create(String name, String category, int price, int stock) {
        return productDao.insert(new Product(null, name, category, price, stock, null)).getEntity();
    }

    @Transactional
    public Product addStock(Long id, int delta) {
        if (productDao.addStock(id, delta) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品が存在しないか、在庫が不足しています: " + id);
        }
        return productDao.selectById(id).orElseThrow();
    }
}
