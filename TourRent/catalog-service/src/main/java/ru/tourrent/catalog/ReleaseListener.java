package ru.tourrent.catalog;
import ru.tourrent.shared.IntegrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;
@Component
public class ReleaseListener {
    private final JdbcTemplate db;private final ObjectMapper json;
    public ReleaseListener(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    @RabbitListener(queues="catalog.releases") @Transactional
    public void receive(String raw)throws Exception{
        var e=json.readValue(raw,IntegrationEvent.class);
        int inserted=db.update("insert into processed_events(event_id) values(?) on conflict do nothing",e.eventId());
        if(inserted==0)return;
        db.update("delete from reservations where rental_id=?",e.rentalId());
        LoggerFactory.getLogger(getClass()).info("requestId={} CONSUME {} eventId={} released rentalId={}",e.requestId(),e.type(),e.eventId(),e.rentalId());
    }
}
