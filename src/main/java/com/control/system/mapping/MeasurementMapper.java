package com.control.system.mapping;

import com.control.system.domain.entity.Measurement;
import com.control.system.web.dto.request.MeasurementRequest;
import com.control.system.web.dto.response.MeasurementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MeasurementMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Measurement toEntity(MeasurementRequest request);

    MeasurementResponse toResponse(Measurement measurement);
}
