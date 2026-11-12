package ru.tourrent.config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="ru.tourrent")
@EnableScheduling
@org.springframework.cloud.config.server.EnableConfigServer
public class ConfigServerApplication { public static void main(String[] args) { SpringApplication.run(ConfigServerApplication.class,args); } }
