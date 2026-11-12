package ru.tourrent.shared;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Business writes call append inside their own database transaction. */
@Component
public class Outbox {
    private static final Logger log=LoggerFactory.getLogger(Outbox.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;
    public Outbox(JdbcTemplate jdbc,ObjectMapper mapper,RabbitTemplate rabbit) {this.jdbc=jdbc;this.mapper=mapper;this.rabbit=rabbit;}
    public void append(IntegrationEvent event) {
        try {jdbc.update("insert into outbox(event_id,routing_key,payload) values (?,?,?)",event.eventId(),event.type(),mapper.writeValueAsString(event));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    @Scheduled(fixedDelayString="${tourrent.outbox-delay:1000}")
    public void publish() {
        // Single relay per service in this deployment. Confirmation precedes marking sent.
        var rows=jdbc.queryForList("select event_id,routing_key,payload from outbox where sent_at is null order by created_at,event_id limit 30");
        for(var row:rows) {
            try {
                String raw=(String)row.get("payload");
                IntegrationEvent event=mapper.readValue(raw,IntegrationEvent.class);
                rabbit.invoke(ops->{ops.convertAndSend(BrokerConfiguration.EXCHANGE,event.type(),raw,msg->{
                    msg.getMessageProperties().setMessageId(event.eventId().toString());
                    msg.getMessageProperties().setHeader("X-Request-ID",event.requestId());
                    msg.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);return msg;});
                    ops.waitForConfirmsOrDie(3000);return null;});
                jdbc.update("update outbox set sent_at=current_timestamp where event_id=?",event.eventId());
                log.info("requestId={} PUBLISH {} eventId={} rentalId={}",event.requestId(),event.type(),event.eventId(),event.rentalId());
            } catch(Exception e){log.warn("Outbox delivery deferred: {}",e.getMessage());break;}
        }
    }
}
