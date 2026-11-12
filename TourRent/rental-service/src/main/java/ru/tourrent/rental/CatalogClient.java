package ru.tourrent.rental;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@FeignClient(name="catalog-service")
public interface CatalogClient {
    record Equipment(long id,String name,String category,String description,BigDecimal dailyPrice,int stock,String illustration){}
    @GetMapping("/api/equipment/{id}")
    Equipment equipment(@PathVariable("id") long id,@RequestHeader("X-Request-ID") String requestId);
}
