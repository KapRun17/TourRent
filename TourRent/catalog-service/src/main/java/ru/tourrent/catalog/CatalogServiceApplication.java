package ru.tourrent.catalog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="ru.tourrent")
@EnableScheduling

public class CatalogServiceApplication { public static void main(String[] args) { SpringApplication.run(CatalogServiceApplication.class,args); } }
