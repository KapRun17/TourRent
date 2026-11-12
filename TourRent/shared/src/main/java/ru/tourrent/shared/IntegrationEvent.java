package ru.tourrent.shared;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
/** Version 1 integration contract, independent of service database models. */
public record IntegrationEvent(UUID eventId, String type, UUID rentalId, String requestId,
                               Map<String,Object> data, Instant occurredAt) {
    public static IntegrationEvent of(String type, UUID rentalId, String requestId, Map<String,Object> data) {
        return new IntegrationEvent(UUID.randomUUID(), type, rentalId, requestId, data, Instant.now());
    }
}
