package com.control.system.repository.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

/**
 * Small shared helpers for the custom repository fragments: a date-range criteria builder
 * and a paginated execution that returns a {@link Page} with a default {@code createdAt} sort.
 */
public final class MongoQuerySupport {

    private MongoQuerySupport() {
    }

    public static void addDateRange(final Query query, final String field, final Instant from, final Instant to) {
        if (from == null && to == null) {
            return;
        }
        Criteria criteria = Criteria.where(field);
        if (from != null) {
            criteria = criteria.gte(from);
        }
        if (to != null) {
            criteria = criteria.lte(to);
        }
        query.addCriteria(criteria);
    }

    public static void addNumericRange(final Query query, final String field, final Double min, final Double max) {
        if (min == null && max == null) {
            return;
        }
        Criteria criteria = Criteria.where(field);
        if (min != null) {
            criteria = criteria.gte(min);
        }
        if (max != null) {
            criteria = criteria.lte(max);
        }
        query.addCriteria(criteria);
    }

    public static <T> Page<T> findPage(
        final MongoTemplate mongoTemplate,
        final Query query,
        final Pageable pageable,
        final Class<T> type
    ) {
        final long total = mongoTemplate.count(query, type);
        query.with(pageable);
        if (pageable.getSort().isUnsorted()) {
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        final List<T> content = mongoTemplate.find(query, type);
        return new PageImpl<>(content, pageable, total);
    }
}
