package com.spring.boot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class SpringBootK8sApplication {

    public SpringBootK8sApplication(ApplicationConfig config) {
        this.config = config;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringBootK8sApplication.class, args);
    }

    private final ApplicationConfig config;

    @Scheduled(fixedDelay = 30000)
    public void hello() {
        log.info("The message is:  {} ", config.getMessage());
    }
}
