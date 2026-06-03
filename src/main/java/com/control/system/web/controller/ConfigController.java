package com.control.system.web.controller;

import com.control.system.infrastructure.web.ClientIpResolver;
import com.control.system.service.ConfigService;
import com.control.system.web.dto.request.ConfigHistoryQuery;
import com.control.system.web.dto.request.ConfigRequest;
import com.control.system.web.dto.response.ConfigResponse;
import com.control.system.web.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "Threshold configuration management")
public class ConfigController {

    private final ConfigService configService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new configuration (becomes the active one)")
    public ConfigResponse createConfig(
        @Valid @RequestBody final ConfigRequest request,
        final HttpServletRequest httpRequest
    ) {
        final String clientIp = clientIpResolver.resolve(httpRequest);
        final String userAgent = httpRequest.getHeader("User-Agent");
        return configService.createConfig(request, clientIp, userAgent);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the current active configuration")
    public ConfigResponse getLatestConfig() {
        return configService.getLatestConfig();
    }

    @GetMapping("/history")
    @Operation(summary = "Get paginated configuration history with optional column filters")
    public PageResponse<ConfigResponse> getConfigHistory(
        final ConfigHistoryQuery query,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return configService.searchConfigHistory(query.toSearchFilter(), pageable);
    }
}
