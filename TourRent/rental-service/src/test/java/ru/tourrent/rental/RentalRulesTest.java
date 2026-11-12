package ru.tourrent.rental;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class RentalRulesTest {
    @Test void chargesPerDayAndUnit(){var start=LocalDate.now().plusDays(2);assertEquals(new BigDecimal("4140.00"),RentalRules.price(new BigDecimal("690.00"),start,start.plusDays(3),2));}
    @Test void rejectsZeroLength(){var d=LocalDate.now();assertThrows(IllegalArgumentException.class,()->RentalRules.price(BigDecimal.TEN,d,d,1));}
    @Test void rejectsPast(){var d=LocalDate.now().minusDays(1);assertThrows(IllegalArgumentException.class,()->RentalRules.price(BigDecimal.TEN,d,d.plusDays(2),1));}
    @Test void rejectsMoreThanThirtyDays(){var d=LocalDate.now().plusDays(1);assertThrows(IllegalArgumentException.class,()->RentalRules.price(BigDecimal.TEN,d,d.plusDays(31),1));}
    @Test void rejectsZeroQuantity(){var d=LocalDate.now().plusDays(1);assertThrows(IllegalArgumentException.class,()->RentalRules.price(BigDecimal.TEN,d,d.plusDays(2),0));}
    @Test void onlyEarlyRentalsCanBeCancelled(){assertTrue(RentalRules.canCancel("CONFIRMED"));assertTrue(RentalRules.canCancel("DELIVERY_CREATED"));assertFalse(RentalRules.canCancel("IN_TRANSIT"));assertFalse(RentalRules.canCancel("COMPLETED"));}
    @Test void returnStartsAfterDelivery(){assertTrue(RentalRules.canReturn("DELIVERED"));assertFalse(RentalRules.canReturn("IN_TRANSIT"));}
    @Test void staleEventsCannotRegressState(){assertFalse(RentalRules.accepts("DELIVERED","IN_TRANSIT"));assertFalse(RentalRules.accepts("CANCELLED","DELIVERY_CREATED"));assertTrue(RentalRules.accepts("RETURN_IN_TRANSIT","COMPLETED"));}
}
