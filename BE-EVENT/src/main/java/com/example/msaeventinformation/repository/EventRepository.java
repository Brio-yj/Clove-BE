package com.example.msaeventinformation.repository;

import com.example.msaeventinformation.model.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByName(String name);
    List<Event> findByNamespace(String namespace);
}