package ru.tourrent.logistics;
public final class LogisticsRules {
    private LogisticsRules(){}
    public static String next(String status){return switch(status){
        case "DELIVERY_CREATED" -> "IN_TRANSIT";
        case "IN_TRANSIT" -> "DELIVERED";
        case "RETURN_REQUESTED" -> "RETURN_IN_TRANSIT";
        case "RETURN_IN_TRANSIT" -> "COMPLETED";
        default -> null;
    };}
}
