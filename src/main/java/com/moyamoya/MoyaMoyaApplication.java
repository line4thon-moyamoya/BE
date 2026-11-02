package com.moyamoya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@ConfigurationPropertiesScan
@SpringBootApplication
public class MoyaMoyaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyaMoyaApplication.class, args);
    }
}

