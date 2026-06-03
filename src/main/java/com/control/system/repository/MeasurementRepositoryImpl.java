package com.control.system.repository;

import com.control.system.domain.entity.Measurement;
import com.control.system.repository.filter.MeasurementSearchFilter;
import com.control.system.repository.support.MongoQuerySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RequiredArgsConstructor
public class MeasurementRepositoryImpl implements MeasurementRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Measurement> search(final MeasurementSearchFilter filter, final Pageable pageable) {
        final Query query = new Query();
        MongoQuerySupport.addDateRange(query, "createdAt", filter.from(), filter.to());
        MongoQuerySupport.addNumericRange(query, "temperature", filter.temperatureMin(), filter.temperatureMax());
        MongoQuerySupport.addNumericRange(query, "humidity", filter.humidityMin(), filter.humidityMax());
        if (filter.status() != null) {
            query.addCriteria(Criteria.where("status").is(filter.status()));
        }
        if (filter.coolerOn() != null) {
            query.addCriteria(Criteria.where("coolerOn").is(filter.coolerOn()));
        }
        return MongoQuerySupport.findPage(mongoTemplate, query, pageable, Measurement.class);
    }
}
