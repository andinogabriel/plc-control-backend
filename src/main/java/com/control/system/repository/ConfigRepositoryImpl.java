package com.control.system.repository;

import com.control.system.domain.entity.Config;
import com.control.system.infrastructure.text.DiacriticInsensitiveRegex;
import com.control.system.repository.filter.ConfigSearchFilter;
import com.control.system.repository.support.MongoQuerySupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Config> search(final ConfigSearchFilter filter, final Pageable pageable) {
        final Query query = new Query();
        MongoQuerySupport.addDateRange(query, "createdAt", filter.from(), filter.to());
        addContains(query, "createdByName", filter.createdByName());
        addContains(query, "createdByEmail", filter.createdByEmail());
        addExactMatch(query, "temperatureMin", filter.temperatureMin());
        addExactMatch(query, "temperatureMax", filter.temperatureMax());
        addExactMatch(query, "humidityMin", filter.humidityMin());
        addExactMatch(query, "humidityMax", filter.humidityMax());
        return MongoQuerySupport.findPage(mongoTemplate, query, pageable, Config.class);
    }

    /** Case- and accent-insensitive "contains" against the real (accented) stored field. */
    private void addContains(final Query query, final String field, final String value) {
        final String pattern = DiacriticInsensitiveRegex.containsPattern(value);
        if (StringUtils.isBlank(pattern)) {
            return;
        }
        query.addCriteria(Criteria.where(field).regex(pattern, "i"));
    }

    private void addExactMatch(final Query query, final String field, final Double value) {
        if (value != null) {
            query.addCriteria(Criteria.where(field).is(value));
        }
    }
}
