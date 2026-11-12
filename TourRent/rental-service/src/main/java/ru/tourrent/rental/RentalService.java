package ru.tourrent.rental;
import ru.tourrent.shared.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Service
public class RentalService {
    private final JdbcTemplate db;private final TransactionTemplate tx;private final CatalogAccess catalog;private final Outbox outbox;private final ObjectMapper mapper;
    public RentalService(JdbcTemplate db,TransactionTemplate tx,CatalogAccess catalog,Outbox outbox,ObjectMapper mapper){this.db=db;this.tx=tx;this.catalog=catalog;this.outbox=outbox;this.mapper=mapper;}
    public record Create(@Positive long equipmentId,@NotNull LocalDate startDate,@NotNull LocalDate endDate,@Min(1) @Max(20) int quantity,@NotBlank @Size(max=100) String recipient,@NotBlank @Size(max=250) String address){}
    public List<Map<String,Object>> list(String owner){return db.queryForList("select * from rentals where owner_id=? order by created_at desc",owner);}
    public Map<String,Object> one(UUID id,String owner){return db.queryForList("select * from rentals where id=? and owner_id=?",id,owner).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Аренда не найдена"));}
    public Map<String,Object> create(Create c,String owner,String requestId){
        // Reject malformed dates before performing any remote side effect.
        try{RentalRules.price(BigDecimal.ONE,c.startDate(),c.endDate(),c.quantity());}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
        var item=catalog.equipment(c.equipmentId(),requestId);
        BigDecimal amount=RentalRules.price(item.dailyPrice(),c.startDate(),c.endDate(),c.quantity());
        UUID id=UUID.randomUUID();
        catalog.reserve(Map.of("rentalId",id,"equipmentId",c.equipmentId(),"startDate",c.startDate(),"endDate",c.endDate(),"quantity",c.quantity()),requestId);
        try{return tx.execute(s->{
            db.update("insert into rentals(id,owner_id,equipment_id,equipment_name,start_date,end_date,quantity,recipient,address,total,status,request_id) values(?,?,?,?,?,?,?,?,?,?,?,?)",id,owner,c.equipmentId(),item.name(),c.startDate(),c.endDate(),c.quantity(),c.recipient(),c.address(),amount,"CONFIRMED",requestId);
            history(id,"CONFIRMED",requestId);
            outbox.append(IntegrationEvent.of("rental.confirmed",id,requestId,Map.of("recipient",c.recipient(),"address",c.address(),"ownerId",owner)));
            LoggerFactory.getLogger(getClass()).info("requestId={} CREATED rentalId={} equipment={}",requestId,id,c.equipmentId());
            return one(id,owner);
        });}catch(RuntimeException e){
            try{catalog.release(id,requestId);}catch(Exception compensation){LoggerFactory.getLogger(getClass()).error("requestId={} reservation compensation required rentalId={}",requestId,id,compensation);}
            throw e;
        }
    }
    public Map<String,Object> action(UUID id,String action,String owner,String requestId){
        return tx.execute(s->{
            var rows=db.queryForList("select * from rentals where id=? and owner_id=? for update",id,owner);
            if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Аренда не найдена");
            String status=(String)rows.getFirst().get("status");
            String next;
            if(action.equals("cancel")&&RentalRules.canCancel(status))next="CANCELLED";
            else if(action.equals("return")&&RentalRules.canReturn(status))next="RETURN_REQUESTED";
            else throw new ResponseStatusException(HttpStatus.CONFLICT,"Действие недоступно в состоянии "+status);
            db.update("update rentals set status=? where id=?",next,id);history(id,next,requestId);
            outbox.append(IntegrationEvent.of("rental."+next.toLowerCase(Locale.ROOT),id,requestId,Map.of()));
            return one(id,owner);
        });
    }
    public List<Map<String,Object>> history(UUID id,long after){return db.queryForList("select sequence,status,request_id,occurred_at from rental_history where rental_id=? and sequence>? order by sequence",id,after);}
    private void history(UUID id,String state,String rid){db.update("insert into rental_history(rental_id,status,request_id) values(?,?,?)",id,state,rid);}
    @RabbitListener(queues="rentals.logistics")
    public void consume(String raw)throws Exception{
        var e=mapper.readValue(raw,IntegrationEvent.class);
        tx.executeWithoutResult(s->{
            int inserted=db.update("insert into processed_events(event_id) values(?) on conflict do nothing",e.eventId());
            if(inserted==0){LoggerFactory.getLogger(getClass()).info("requestId={} DUPLICATE eventId={}",e.requestId(),e.eventId());return;}
            var rows=db.queryForList("select status from rentals where id=? for update",e.rentalId());
            if(rows.isEmpty())throw new IllegalStateException("Rental event arrived before aggregate");
            String next=(String)e.data().get("status"),current=(String)rows.getFirst().get("status");
            if(RentalRules.accepts(current,next)){
                db.update("update rentals set status=? where id=?",next,e.rentalId());history(e.rentalId(),next,e.requestId());
                if(next.equals("COMPLETED"))outbox.append(IntegrationEvent.of("rental.completed",e.rentalId(),e.requestId(),Map.of()));
            }
            LoggerFactory.getLogger(getClass()).info("requestId={} CONSUME {} eventId={} rentalId={} status={}",e.requestId(),e.type(),e.eventId(),e.rentalId(),next);
        });
    }
    public Map<String,Object> diagnostics(){return Map.of("circuitBreaker",catalog.breaker.getState().name(),"pendingOutbox",db.queryForObject("select count(*) from outbox where sent_at is null",Integer.class),"configurationSource","git-main");}
}
