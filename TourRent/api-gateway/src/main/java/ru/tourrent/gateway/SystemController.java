package ru.tourrent.gateway;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.*;
import java.time.Duration;
import java.util.*;
@RestController
public class SystemController {
    private final WebClient web;private final ReactiveDiscoveryClient discovery;
    @Value("${tourrent.auth-public:http://localhost:8180}") String auth;
    public SystemController(WebClient.Builder b,ReactiveDiscoveryClient discovery){web=b.build();this.discovery=discovery;}
    @GetMapping("/ui-config") public Map<String,Object> config(){return Map.of("authUrl",auth,"realm","tourrent","clientId","tourrent-web");}
    @GetMapping("/api/system")
    public Mono<Map<String,Object>> system(){
        return Flux.fromIterable(List.of("catalog-service","rental-service","logistics-service"))
            .flatMap(name->discovery.getInstances(name).map(i->i.getInstanceId()).collectList().flatMap(instances->
                web.get().uri("http://"+name+"/actuator/health").retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(3))
                .map(health->Map.<String,Object>of("name",name,"status",health.getOrDefault("status","UNKNOWN"),"instances",instances,"details",health))
                .onErrorReturn(Map.<String,Object>of("name",name,"status","DOWN","instances",instances))))
            .collectList().flatMap(services->web.get().uri("http://rental-service/internal/diagnostics").retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(3)).onErrorReturn(Map.of("circuitBreaker","UNKNOWN"))
                .map(diagnostics->Map.<String,Object>of("services",services,"diagnostics",diagnostics,"auth","OAuth2 / OpenID Connect","broker","RabbitMQ","configuration","Spring Cloud Config / Git")));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> error(Exception e,ServerWebExchange exchange){
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Сервис временно недоступен. Повторите запрос позже");
        p.setProperty("requestId",exchange.getRequest().getHeaders().getFirst("X-Request-ID"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(p);
    }
}
