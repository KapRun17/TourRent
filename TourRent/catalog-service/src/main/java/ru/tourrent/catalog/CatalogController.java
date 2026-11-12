package ru.tourrent.catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
@RestController
public class CatalogController {
    private static final Logger log=LoggerFactory.getLogger(CatalogController.class);
    private final JdbcTemplate db;private final TransactionTemplate tx;
    private final String instance=System.getenv().getOrDefault("HOSTNAME","local");
    public CatalogController(JdbcTemplate db,TransactionTemplate tx){this.db=db;this.tx=tx;}
    public record Equipment(long id,String name,String category,String description,BigDecimal dailyPrice,int stock,String illustration){}
    public record Hold(@NotNull UUID rentalId,@Positive long equipmentId,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@Min(1) @Max(20) int quantity){}
    @GetMapping("/api/equipment")
    public ResponseEntity<List<Equipment>> list(@RequestHeader(value="X-Request-ID",defaultValue="unknown") String rid){
        log.info("requestId={} LIST equipment instance={}",rid,instance);
        var items=db.query("select * from equipment order by id",(r,n)->new Equipment(r.getLong("id"),r.getString("name"),r.getString("category"),r.getString("description"),r.getBigDecimal("daily_price"),r.getInt("stock"),r.getString("illustration")));
        return ResponseEntity.ok().header("X-Service-Instance",instance).body(items);
    }
    @GetMapping("/api/equipment/{id}")
    public Equipment one(@PathVariable long id,@RequestHeader(value="X-Request-ID",defaultValue="unknown") String rid){
        log.info("requestId={} GET equipment={} instance={}",rid,id,instance);
        return db.query("select * from equipment where id=?",(r,n)->new Equipment(r.getLong("id"),r.getString("name"),r.getString("category"),r.getString("description"),r.getBigDecimal("daily_price"),r.getInt("stock"),r.getString("illustration")),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Снаряжение не найдено"));
    }
    @GetMapping("/api/equipment/{id}/availability")
    public Map<String,Object> availability(@PathVariable long id,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate,@RequestHeader(value="X-Request-ID",defaultValue="unknown") String rid){
        validateDates(startDate,endDate);
        var item=one(id,rid);return Map.of("equipmentId",id,"available",available(id,startDate,endDate,item.stock()),"startDate",startDate,"endDate",endDate);
    }
    /** Item row lock serializes concurrent reservations, including different service replicas. */
    @PostMapping("/internal/reservations")
    public Map<String,Object> reserve(@Valid @RequestBody Hold hold,@RequestHeader(value="X-Request-ID",defaultValue="unknown") String rid){
        validateDates(hold.startDate(),hold.endDate());
        return tx.execute(s->{
            var items=db.queryForList("select stock from equipment where id=? for update",hold.equipmentId());
            if(items.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Снаряжение не найдено");
            var existing=db.queryForList("select equipment_id,start_date,end_date,quantity from reservations where rental_id=?",hold.rentalId());
            if(!existing.isEmpty()){
                var old=existing.getFirst();
                if(((Number)old.get("equipment_id")).longValue()!=hold.equipmentId()||!old.get("start_date").toString().equals(hold.startDate().toString())||!old.get("end_date").toString().equals(hold.endDate().toString())||((Number)old.get("quantity")).intValue()!=hold.quantity())throw new ResponseStatusException(HttpStatus.CONFLICT,"Идентификатор брони уже используется");
                return Map.of("rentalId",hold.rentalId(),"reserved",true);
            }
            int stock=((Number)items.getFirst().get("stock")).intValue();
            if(available(hold.equipmentId(),hold.startDate(),hold.endDate(),stock)<hold.quantity())throw new ResponseStatusException(HttpStatus.CONFLICT,"На выбранные даты недостаточно свободного снаряжения");
            db.update("insert into reservations(rental_id,equipment_id,start_date,end_date,quantity) values(?,?,?,?,?)",hold.rentalId(),hold.equipmentId(),hold.startDate(),hold.endDate(),hold.quantity());
            log.info("requestId={} RESERVED rentalId={} equipment={}",rid,hold.rentalId(),hold.equipmentId());
            return Map.of("rentalId",hold.rentalId(),"reserved",true);
        });
    }
    @DeleteMapping("/internal/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID id){db.update("delete from reservations where rental_id=?",id);}
    private int available(long id,LocalDate start,LocalDate end,int stock){
        // End date is exclusive: returning and re-renting on the same day is allowed.
        int max=0;
        for(LocalDate day=start;day.isBefore(end);day=day.plusDays(1)){
            int taken=db.queryForObject("select coalesce(sum(quantity),0) from reservations where equipment_id=? and start_date<=? and end_date>?",Integer.class,id,day,day);
            max=Math.max(max,taken);
        }
        return stock-max;
    }
    static void validateDates(LocalDate start,LocalDate end){
        if(start==null||end==null||start.isBefore(LocalDate.now())||!end.isAfter(start)||ChronoUnit.DAYS.between(start,end)>30)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Укажите будущий период аренды от 1 до 30 дней");
    }
}
