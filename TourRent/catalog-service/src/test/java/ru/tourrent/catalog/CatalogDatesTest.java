package ru.tourrent.catalog;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class CatalogDatesTest {
    @Test void acceptsMaximumPeriod(){var d=LocalDate.now().plusDays(1);assertDoesNotThrow(()->CatalogController.validateDates(d,d.plusDays(30)));}
    @Test void rejectsReversedDates(){var d=LocalDate.now().plusDays(2);assertThrows(ResponseStatusException.class,()->CatalogController.validateDates(d,d.minusDays(1)));}
    @Test void rejectsMissingDates(){assertThrows(ResponseStatusException.class,()->CatalogController.validateDates(null,null));}
}
