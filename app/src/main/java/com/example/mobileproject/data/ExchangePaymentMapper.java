package com.example.mobileproject.data;

import java.util.Map;

/** Wallet exchanges hold their own payment record; legacy payments remain readable. */
public final class ExchangePaymentMapper {
    private ExchangePaymentMapper() { }
    public static String status(boolean walletManaged, Map<String,Object> exchange, Map<String,Object> legacy) {
        Object value = (walletManaged ? exchange : legacy) == null ? null
                : (walletManaged ? exchange : legacy).get(walletManaged ? "paymentStatus" : "status");
        return value instanceof String && !((String)value).trim().isEmpty() ? (String)value : "CHƯA CÓ DỮ LIỆU";
    }
    public static double amount(boolean walletManaged, Map<String,Object> exchange, Map<String,Object> legacy) {
        Map<String,Object> source=walletManaged?exchange:legacy;
        Object value=source==null?null:source.get("amount");
        return value instanceof Number?((Number)value).doubleValue():0;
    }
}
