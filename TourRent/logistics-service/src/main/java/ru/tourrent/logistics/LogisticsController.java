package ru.tourrent.logistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class LogisticsController {
    private final JdbcTemplate db;
    public LogisticsController(JdbcTemplate db){this.db=db;}
    @GetMapping("/api/deliveries")
    public List<Map<String,Object>> mine(@RequestHeader("X-User-ID") String owner){return db.queryForList("select * from deliveries where owner_id=? order by created_at desc",owner);}
    // Owner check stays in Rental Service; logistics data is not publicly routed by Gateway.
    @GetMapping("/internal/deliveries/{id}")
    public List<Map<String,Object>> byRental(@PathVariable UUID id){return db.queryForList("select * from deliveries where rental_id=?",id);}
}
