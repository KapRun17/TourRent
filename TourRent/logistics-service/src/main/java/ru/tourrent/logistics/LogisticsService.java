package ru.tourrent.logistics;
import ru.tourrent.shared.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.web.bind.annotation.*;
import org.slf4j.LoggerFactory;
import java.util.*;
@Service
public class LogisticsService {
    private final JdbcTemplate db;private final TransactionTemplate tx;private final Outbox outbox;private final ObjectMapper mapper;
    public LogisticsService(JdbcTemplate db,TransactionTemplate tx,Outbox outbox,ObjectMapper mapper){this.db=db;this.tx=tx;this.outbox=outbox;this.mapper=mapper;}
    @RabbitListener(queues="logistics.rentals")
    public void receive(String raw)throws Exception{
        var e=mapper.readValue(raw,IntegrationEvent.class);
        tx.executeWithoutResult(s->{
            if(db.update("insert into processed_events(event_id) values(?) on conflict do nothing",e.eventId())==0){LoggerFactory.getLogger(getClass()).info("requestId={} DUPLICATE ignored eventId={} rentalId={}",e.requestId(),e.eventId(),e.rentalId());return;}
            if(e.type().equals("rental.confirmed")){
                int added=db.update("insert into deliveries(rental_id,owner_id,recipient,address,status,request_id) values(?,?,?,?,?,?) on conflict(rental_id) do nothing",e.rentalId(),e.data().get("ownerId"),e.data().get("recipient"),e.data().get("address"),"DELIVERY_CREATED",e.requestId());
                if(added>0)publish(e.rentalId(),"DELIVERY_CREATED",e.requestId());
            }else if(e.type().equals("rental.cancelled")){
                db.update("update deliveries set status='CANCELLED',updated_at=current_timestamp where rental_id=?",e.rentalId());
            }else if(e.type().equals("rental.return_requested")){
                int changed=db.update("update deliveries set status='RETURN_REQUESTED',request_id=?,updated_at=current_timestamp where rental_id=? and status='DELIVERED'",e.requestId(),e.rentalId());
                if(changed==0)throw new IllegalStateException("Delivery is not ready for return");
            }
            LoggerFactory.getLogger(getClass()).info("requestId={} CONSUME {} eventId={} rentalId={}",e.requestId(),e.type(),e.eventId(),e.rentalId());
        });
    }
    @Scheduled(fixedDelayString="${tourrent.courier-tick:1000}")
    public void advance(){
        tx.executeWithoutResult(s->{
            var rows=db.queryForList("select * from deliveries where status in ('DELIVERY_CREATED','IN_TRANSIT','RETURN_REQUESTED','RETURN_IN_TRANSIT') and updated_at < current_timestamp - interval '3 seconds' for update skip locked");
            for(var row:rows){
                UUID id=(UUID)row.get("rental_id");String next=LogisticsRules.next((String)row.get("status"));
                db.update("update deliveries set status=?,updated_at=current_timestamp where rental_id=?",next,id);
                publish(id,next,(String)row.get("request_id"));
            }
        });
    }
    private void publish(UUID id,String status,String rid){outbox.append(IntegrationEvent.of("logistics.status",id,rid,Map.of("status",status)));}
}
