package ru.tourrent.logistics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LogisticsRulesTest {
    @Test void outboundRouteEndsAtRecipient(){assertEquals("IN_TRANSIT",LogisticsRules.next("DELIVERY_CREATED"));assertEquals("DELIVERED",LogisticsRules.next("IN_TRANSIT"));assertNull(LogisticsRules.next("DELIVERED"));}
    @Test void returnRouteEndsAtWarehouse(){assertEquals("RETURN_IN_TRANSIT",LogisticsRules.next("RETURN_REQUESTED"));assertEquals("COMPLETED",LogisticsRules.next("RETURN_IN_TRANSIT"));}
    @Test void cancelledAndCompletedNeverMove(){assertNull(LogisticsRules.next("CANCELLED"));assertNull(LogisticsRules.next("COMPLETED"));}
}
