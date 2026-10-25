package com.example.mobileproject.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequestPaymentRoutingTest {
    private RequestItem money(String type){RequestItem item=new RequestItem();item.exchangeType=type;return item;}
    @Test public void bothMoneyAliasesUseCheckout(){assertTrue(money("MONEY").needsWalletPayment());assertTrue(money("NON_RECIPROCAL").needsWalletPayment());}
    @Test public void reciprocalDoesNotUseCheckout(){assertFalse(money("RECIPROCAL").needsWalletPayment());}
    @Test public void walletBookingCannotBeChargedAgain(){RequestItem i=money("MONEY");i.walletManaged=true;assertFalse(i.needsWalletPayment());}
    @Test public void paidLegacyCannotBeChargedAgain(){RequestItem i=money("MONEY");i.isPaid=true;assertFalse(i.needsWalletPayment());}
    @Test public void terminalAndAcceptedRequestsCannotBeCharged(){for(String state:new String[]{"ACCEPTED","CANCELLED","REJECTED","COMPLETED"}){RequestItem i=money("MONEY");i.status=state;assertFalse(i.needsWalletPayment());}}
}
