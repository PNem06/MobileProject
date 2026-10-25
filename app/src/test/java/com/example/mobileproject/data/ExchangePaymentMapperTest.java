package com.example.mobileproject.data;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
public class ExchangePaymentMapperTest {
    private Map<String,Object> data(Object... pairs){Map<String,Object> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    @Test public void walletStatusUsesExchange(){assertEquals("PAID",ExchangePaymentMapper.status(true,data("paymentStatus","PAID"),null));}
    @Test public void walletAmountUsesExchange(){assertEquals(10000,ExchangePaymentMapper.amount(true,data("amount",10000L),null),0);}
    @Test public void staleLegacyDoesNotOverrideWallet(){assertEquals("REFUNDED",ExchangePaymentMapper.status(true,data("paymentStatus","REFUNDED"),data("status","PAID")));}
    @Test public void legacyStatusRemainsReadable(){assertEquals("PAID",ExchangePaymentMapper.status(false,data("status","PENDING"),data("status","PAID")));}
    @Test public void legacyAmountRemainsReadable(){assertEquals(2000,ExchangePaymentMapper.amount(false,data("amount",9999),data("amount",2000)),0);}
    @Test public void missingPaymentIsNotShownAsPaid(){assertEquals("CHƯA CÓ DỮ LIỆU",ExchangePaymentMapper.status(false,null,null));}
    @Test public void missingWalletAmountDoesNotUseOldPayment(){assertEquals(0,ExchangePaymentMapper.amount(true,data(),data("amount",99999)),0);}
}
