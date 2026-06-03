package com.control.system.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI controlSystemOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Sistema de Control PLC - API")
            .description("""
                Backend del sistema de control de temperatura/humedad
                (Raspberry Pi 3B+ + sensor DHT + OpenPLC + relay + cooler).
                Gestiona la configuración versionada de umbrales (con auditoría),
                la ingesta de mediciones y las consultas con filtros server-side.
                Proyecto de Teoría de Control - UNCAUS 2026.""")
            .version("1.0.0")
            .contact(new Contact().name("Gabriel Andino").email("gabriel@example.com"))
            .license(new License().name("UNCAUS - Teoría de Control 2026")));
    }
}
