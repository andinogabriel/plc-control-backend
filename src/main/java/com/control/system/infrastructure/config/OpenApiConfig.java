package com.control.system.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI controlSystemOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Control System API")
            .description("Temperature/humidity control system backend (Raspberry Pi + DHT + OpenPLC)")
            .version("1.0.0"));
    }
}
