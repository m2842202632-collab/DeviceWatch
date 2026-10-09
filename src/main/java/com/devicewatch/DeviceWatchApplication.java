package com.devicewatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeviceWatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceWatchApplication.class, args);
    }
}
