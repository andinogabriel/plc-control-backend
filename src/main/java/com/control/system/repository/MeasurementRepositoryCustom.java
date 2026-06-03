package com.control.system.repository;

import com.control.system.domain.entity.Measurement;
import com.control.system.repository.filter.MeasurementSearchFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MeasurementRepositoryCustom {

    Page<Measurement> search(MeasurementSearchFilter filter, Pageable pageable);
}
