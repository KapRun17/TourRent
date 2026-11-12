package ru.tourrent.rental;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
@Component
public class CatalogAccess {
    private final CatalogClient feign;private final RestClient rest;
    final CircuitBreaker breaker=CircuitBreaker.of("catalog",CircuitBreakerConfig.custom().slidingWindowSize(4).minimumNumberOfCalls(2).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(8)).permittedNumberOfCallsInHalfOpenState(1).ignoreExceptions(ResponseStatusException.class,HttpClientErrorException.class).build());
    private final Retry retry=Retry.of("catalog-read",RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(200)).retryOnException(e->e instanceof FeignException f && (f.status()<0||f.status()>=500)).build());
    public CatalogAccess(CatalogClient feign,RestClient.Builder builder){this.feign=feign;this.rest=builder.build();}
    public CatalogClient.Equipment equipment(long id,String requestId){
        try{return breaker.executeSupplier(()->retry.executeSupplier(()->{
            try{return feign.equipment(id,requestId);}catch(FeignException.NotFound e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Снаряжение не найдено");}
        }));}catch(ResponseStatusException e){throw e;}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("requestId={} Catalog lookup failed: {}",requestId,e.toString());throw unavailable();}
    }
    public void reserve(Object body,String requestId){
        try{breaker.executeRunnable(()->rest.post().uri("http://catalog-service/internal/reservations").header("X-Request-ID",requestId).body(body).retrieve().toBodilessEntity());}
        catch(HttpClientErrorException e){throw new ResponseStatusException(e.getStatusCode(), e.getStatusCode().value()==409?"На выбранные даты недостаточно свободного снаряжения":"Некорректный период аренды");}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("requestId={} Catalog reservation failed: {}",requestId,e.toString());throw unavailable();}
    }
    public void release(UUID id,String requestId){rest.delete().uri("http://catalog-service/internal/reservations/{id}",id).header("X-Request-ID",requestId).retrieve().toBodilessEntity();}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Каталог временно недоступен. Повторите попытку через несколько секунд");}
    @Configuration
    static class HttpConfiguration {
        // Boot does not auto-create servlet message converters in a WebFlux application.
        // OpenFeign's SpringDecoder still requires them for the Equipment response.
        @Bean org.springframework.boot.autoconfigure.http.HttpMessageConverters feignMessageConverters(com.fasterxml.jackson.databind.ObjectMapper mapper){
            return new org.springframework.boot.autoconfigure.http.HttpMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper));
        }
        @Bean @LoadBalanced RestClient.Builder restClientBuilder(){
            var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(1200);f.setReadTimeout(2000);
            return RestClient.builder().requestFactory(f);
        }
    }
}
