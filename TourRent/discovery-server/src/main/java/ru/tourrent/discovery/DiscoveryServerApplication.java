package ru.tourrent.discovery;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="ru.tourrent")
@EnableScheduling
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
public class DiscoveryServerApplication { public static void main(String[] args) { SpringApplication.run(DiscoveryServerApplication.class,args); } }
