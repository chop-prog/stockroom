package com.chopprog.stockroom;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockroomApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockroomApplication.class, args);
    }

    /** Time comes from a bean so tests can move it forward instead of sleeping. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
