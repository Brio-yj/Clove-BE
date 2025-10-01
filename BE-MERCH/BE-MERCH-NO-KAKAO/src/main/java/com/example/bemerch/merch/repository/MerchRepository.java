package com.example.bemerch.merch.repository;


import com.example.bemerch.merch.model.Merch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchRepository extends JpaRepository<Merch, Long> {
    boolean existsByNameAndPriceAndEventName(String name, int price, String eventName);
    int countByNameAndPriceAndEventName(String name, int price,String eventName);
    int countByNameAndPriceAndEventNameAndSoldTrue(String name, int price, String eventName);

    List<Merch> findByEmail(String email);

    Optional<Merch> findFirstByNameAndPriceAndEventNameAndSoldIsFalse(String name, int price, String eventName);
    Optional<Merch> findByNameAndPriceAndEventNameAndTidAndEmailAndMid(String name,int price,String eventName,String tid,String email, String mid);

}