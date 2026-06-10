package com.control.system;

import com.control.system.infrastructure.config.StreamProperties;
import com.control.system.infrastructure.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableMongoAuditing
@EnableScheduling
@EnableConfigurationProperties({ RateLimitProperties.class, StreamProperties.class })
public class ControlSystemApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ControlSystemApplication.class, args);
    }
}
