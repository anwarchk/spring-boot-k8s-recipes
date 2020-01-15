package com.spring.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "greeting")
@Data
public class ApplicationConfig {

    private String message = "a message that can be live replaced";

}
