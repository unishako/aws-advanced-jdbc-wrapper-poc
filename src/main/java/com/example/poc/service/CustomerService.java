package com.example.poc.service;

import com.example.poc.repository.CustomerRepository;
import com.example.poc.entity.Customer;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<Customer> list(int limit, int offset) {
        return customerRepository.findAll(limit, offset);
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "顧客が存在しません: " + id));
    }

    @Transactional(readOnly = true)
    public long count() {
        return customerRepository.count();
    }

    @Transactional
    public Customer create(String name, String email, String prefecture) {
        return customerRepository.insert(new Customer(null, name, email, prefecture, null));
    }
}
