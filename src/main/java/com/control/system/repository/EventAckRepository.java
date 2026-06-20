package com.control.system.repository;

import com.control.system.domain.entity.EventAck;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventAckRepository extends MongoRepository<EventAck, String> {
}
