package ru.tourrent.shared;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class IntegrationEventTest {
    @Test void eventRetainsCorrelationAcrossSerialization()throws Exception{
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        var event=IntegrationEvent.of("rental.confirmed",UUID.randomUUID(),"demo-trace",Map.of("recipient","Прокофьев И.Р."));
        assertEquals(event,mapper.readValue(mapper.writeValueAsString(event),IntegrationEvent.class));
    }
    @Test void repeatedBusinessEventsHaveDistinctTransportIdentifiers(){var rental=UUID.randomUUID();assertNotEquals(IntegrationEvent.of("rental.confirmed",rental,"r",Map.of()).eventId(),IntegrationEvent.of("rental.confirmed",rental,"r",Map.of()).eventId());}
}
