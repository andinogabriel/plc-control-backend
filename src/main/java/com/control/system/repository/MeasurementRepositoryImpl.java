package com.control.system.repository;

import com.control.system.domain.entity.Measurement;
import com.control.system.repository.filter.MeasurementSearchFilter;
import com.control.system.repository.support.MongoQuerySupport;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@RequiredArgsConstructor
public class MeasurementRepositoryImpl implements MeasurementRepositoryCustom {

    /** Upper bound on a down-sampled chart series, regardless of the requested maxPoints. */
    private static final int MAX_POINTS_CAP = 5000;

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

        final Integer requested = filter.maxPoints();
        if (requested != null && requested > 0) {
            final int maxPoints = Math.min(requested, MAX_POINTS_CAP);
            final long total = mongoTemplate.count(query, Measurement.class);
            if (total > maxPoints) {
                return downsample(query, total, maxPoints);
            }
        }
        return MongoQuerySupport.findPage(mongoTemplate, query, pageable, Measurement.class);
    }

    /**
     * Down-samples a wide range to ~{@code maxPoints} real points by keeping every k-th document
     * (k = ceil(total / maxPoints)) in time order, so the chart shows the whole span instead of
     * only the most recent page. Stride sampling (not averaging) keeps the real reading shape;
     * content is newest-first to match the normal query.
     */
    private Page<Measurement> downsample(final Query query, final long total, final int maxPoints) {
        final long stride = (long) Math.ceil((double) total / maxPoints);

        // Number the matched docs in chronological order, keep every stride-th, then return newest-first.
        final AggregationOperation match = ctx -> new Document("$match", query.getQueryObject());
        final AggregationOperation number = ctx -> new Document("$setWindowFields", new Document()
            .append("sortBy", new Document("createdAt", 1))
            .append("output", new Document("_rn", new Document("$documentNumber", new Document()))));
        final AggregationOperation keepEveryStride = ctx -> new Document("$match", new Document("$expr",
            new Document("$eq", List.of(
                new Document("$mod", List.of(new Document("$subtract", List.of("$_rn", 1L)), stride)), 0L))));
        final AggregationOperation newestFirst = ctx -> new Document("$sort", new Document("createdAt", -1));

        final Aggregation aggregation = Aggregation.newAggregation(match, number, keepEveryStride, newestFirst);
        final List<Measurement> content = mongoTemplate
            .aggregate(aggregation, Measurement.class, Measurement.class)
            .getMappedResults();
        return new PageImpl<>(content, PageRequest.of(0, Math.max(1, content.size())), content.size());
    }
}
