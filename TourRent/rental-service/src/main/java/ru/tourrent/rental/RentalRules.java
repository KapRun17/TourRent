package ru.tourrent.rental;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
public final class RentalRules {
    private RentalRules(){}
    public static BigDecimal price(BigDecimal daily,LocalDate start,LocalDate end,int quantity){
        long days=ChronoUnit.DAYS.between(start,end);
        if(start.isBefore(LocalDate.now())||days<1||days>30||quantity<1||quantity>20)throw new IllegalArgumentException("Укажите будущий период от 1 до 30 дней и количество от 1 до 20");
        return daily.multiply(BigDecimal.valueOf(days)).multiply(BigDecimal.valueOf(quantity));
    }
    public static boolean canCancel(String state){return Set.of("CONFIRMED","DELIVERY_CREATED").contains(state);}
    public static boolean canReturn(String state){return "DELIVERED".equals(state);}
    public static boolean accepts(String current,String next){
        return switch(current){
            case "CONFIRMED" -> "DELIVERY_CREATED".equals(next);
            case "DELIVERY_CREATED" -> "IN_TRANSIT".equals(next);
            case "IN_TRANSIT" -> "DELIVERED".equals(next);
            case "RETURN_REQUESTED" -> "RETURN_IN_TRANSIT".equals(next);
            case "RETURN_IN_TRANSIT" -> "COMPLETED".equals(next);
            default -> false;
        };
    }
}
