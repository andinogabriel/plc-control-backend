package com.control.system.mapping;

import com.control.system.domain.entity.Config;
import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConfigMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "clientIp", ignore = true)
    @Mapping(target = "userAgent", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Config toEntity(ConfigRequest request);

    ConfigResponse toResponse(Config config);
}
