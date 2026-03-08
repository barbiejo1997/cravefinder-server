package com.cravefinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================================
 * WHAT IS THIS FILE?
 * ============================================================
 * Every Java program needs one "main" starting point —
 * this is it. When Render.com runs your server, it finds
 * this file first and boots everything up.
 *
 * @SpringBootApplication tells Spring Boot:
 * "Scan this whole project, set everything up automatically."
 * One annotation does an enormous amount of work for us!
 * ============================================================
 */
@SpringBootApplication
public class CraveFinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(CraveFinderApplication.class, args);
        System.out.println("✅ CraveFinder server is running!");
    }
}
