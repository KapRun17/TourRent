package ru.tourrent.rental;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="ru.tourrent")
@EnableScheduling
@org.springframework.cloud.openfeign.EnableFeignClients
public class RentalServiceApplication { public static void main(String[] args) { SpringApplication.run(RentalServiceApplication.class,args); } }
