package com.spring.boot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient
@Slf4j
@RestController
@RequestMapping("/sayHello")
public class SpringBootK8sApplication {

    private final ApplicationConfig config;
    private final DiscoveryClient discoveryClient;

    public SpringBootK8sApplication(ApplicationConfig config, DiscoveryClient discoveryClient) {
        this.config = config;
        this.discoveryClient = discoveryClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringBootK8sApplication.class, args);
    }

    @Scheduled(fixedDelay = 30000)
    public void hello() {
        log.info("The message is:  {} ", config.getMessage());
    }

    @GetMapping
    public String sayHello() {
        List<ServiceInstance> instances = discoveryClient.getInstances("hello.service");
        URI uri = instances.get(0).getUri();
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForEntity(uri, String.class).getBody();
    }
}
