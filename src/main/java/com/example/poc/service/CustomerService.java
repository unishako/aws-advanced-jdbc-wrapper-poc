package com.example.poc.service;

import com.example.poc.dao.CustomerDao;
import com.example.poc.entity.Customer;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerService {

    private final CustomerDao customerDao;

    public CustomerService(CustomerDao customerDao) {
        this.customerDao = customerDao;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(int limit, int offset) {
        return customerDao.selectPage(limit, offset);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customerDao.selectById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "顧客が存在しません: " + id));
    }

    @Transactional(readOnly = true)
    public long count() {
        return customerDao.count();
    }

    @Transactional
    public Customer create(String name, String email, String prefecture) {
        return customerDao.insert(new Customer(null, name, email, prefecture, null)).getEntity();
    }
}
