package com.ecibet.game5inline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Game5InlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(Game5InlineApplication.class, args);
    }
}