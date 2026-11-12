package ru.tourrent.logistics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="ru.tourrent")
@EnableScheduling

public class LogisticsServiceApplication { public static void main(String[] args) { SpringApplication.run(LogisticsServiceApplication.class,args); } }
