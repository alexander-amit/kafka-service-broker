package io.pivotal.cf.service.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.java.ServiceScan;

@SpringBootApplication
@ServiceScan
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}