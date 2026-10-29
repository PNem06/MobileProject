package com.example.mobileproject.notifications;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Intent;
import com.example.mobileproject.data.HomePostMapper;
import com.example.mobileproject.view.ChatDetailActivity;
import com.example.mobileproject.view.ReceivedRequestsDetailActivity;
import com.example.mobileproject.view.SentRequestDetailActivity;
import com.example.mobileproject.view.WalletActivity;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
public class NotificationRoutingTest {
    private Map<String,Object> data(Object... pairs) {
        Map<String,Object> out = new HashMap<>();
        for (int i=0;i<pairs.length;i+=2) out.put((String)pairs[i],pairs[i+1]);
        return out;
    }
    private NotificationTarget target(Object... pairs) { return NotificationTarget.resolve(new Notice("n",data(pairs))); }
    @Test public void approvedPostUsesExplicitPostId() {
        NotificationTarget t=target("type","POST_APPROVED","postId","post-1");
        assertEquals(NotificationTarget.Kind.POST,t.kind); assertEquals("post-1",t.id);
    }
    @Test public void requestIdNeverBecomesAPostId() {
        NotificationTarget t=target("type","exchange_request","requestId","request-1","postId","post-1");
        assertEquals(NotificationTarget.Kind.REQUEST,t.kind); assertEquals("request-1",t.id);
        assertEquals("exchangeRequests",t.collection);
    }
    @Test public void currentExchangeNoticePrefersExchangeCollection() {
        NotificationTarget t=target("type","exchange_request","exchangeId","request-1","requestId","request-1");
        assertEquals("exchanges",t.collection);
    }
    @Test public void chatIsNotMistakenForPostOrRequest() {
        NotificationTarget t=target("type","chat_message","senderId","partner","conversationId","room","postId","irrelevant");
        assertEquals(NotificationTarget.Kind.CHAT,t.kind); assertEquals("partner",t.id); assertEquals("room",t.collection);
    }
    @Test public void topupNoticeWithoutBookingOpensWalletHome() {
        NotificationTarget t=target("type","wallet","walletManaged",true);
        assertEquals(NotificationTarget.Kind.WALLET,t.kind); assertEquals("",t.id);
    }
    @Test public void withdrawalNoticeUsesWithdrawalDetailInsteadOfBookingDetail() {
        NotificationTarget t=target("type","wallet","walletManaged",true,"withdrawalId","RUT123","exchangeId","");
        assertEquals(NotificationTarget.Kind.WALLET,t.kind);assertEquals("RUT123",t.id);assertEquals("withdrawals",t.collection);
    }
    @Test public void withdrawalTargetSurvivesTheHomeCacheMapper() {
        org.json.JSONObject json=HomePostMapper.fromDocument("n",data("type","wallet","walletManaged",true,"withdrawalId","RUT123"));
        NotificationTarget t=NotificationTarget.resolve(Notice.fromJson(json));
        assertEquals("RUT123",t.id);assertEquals("withdrawals",t.collection);
    }
    @Test public void legacyNoticeWithoutTargetHasInformationFallback() {
        assertEquals(NotificationTarget.Kind.INFO,target("type","post_approved","body","Approved").kind);
    }
    @Test public void readFlagsMergeWithoutReplayingOldReadNotice() {
        assertTrue(new Notice("n",data("isRead",true,"read",false)).read());
        assertTrue(new Notice("n",data("isRead",false,"read",true)).read());
        assertFalse(new Notice("n",data("isRead",false)).read());
    }
    @Test public void mapperUsesCanonicalReadWriteFieldAndKeepsTargetMetadata() throws Exception {
        org.json.JSONObject json=HomePostMapper.fromDocument("n",data("isRead",true,"read",false,"senderId","partner","conversationId","room"));
        assertTrue(json.getBoolean("read")); assertEquals("isRead",json.getString("readField"));
        assertEquals("partner",Notice.fromJson(json).text("senderId"));
    }
    @Test public void hostOpensExactReceivedRequestIncludingLegacySource() {
        Intent i=NotificationNavigator.requestIntent(RuntimeEnvironment.getApplication(),"r","exchangeRequests",data("hostId","host-uid","postId","p"),"host-uid","account-id");
        assertEquals(ReceivedRequestsDetailActivity.class.getName(),i.getComponent().getClassName());
        assertEquals("r",i.getStringExtra(ReceivedRequestsDetailActivity.EXTRA_FOCUS_REQUEST_ID));
        assertEquals("exchangeRequests",i.getStringExtra(ReceivedRequestsDetailActivity.EXTRA_FOCUS_COLLECTION));
    }
    @Test public void accountAliasAlsoIdentifiesReceiver() {
        Intent i=NotificationNavigator.requestIntent(RuntimeEnvironment.getApplication(),"r","exchanges",data("host_accountId","legacy-account","postId","p"),"host-uid","legacy-account");
        assertEquals(ReceivedRequestsDetailActivity.class.getName(),i.getComponent().getClassName());
    }
    @Test public void senderOpensSentDetailForCorrectCollection() {
        Intent i=NotificationNavigator.requestIntent(RuntimeEnvironment.getApplication(),"r","exchangeRequests",data("hostId","other","postId","p"),"sender","sender-account");
        assertEquals(SentRequestDetailActivity.class.getName(),i.getComponent().getClassName());
        assertEquals("exchangeRequests",i.getStringExtra(SentRequestDetailActivity.EXTRA_SOURCE_COLLECTION));
    }
    @Test public void walletRequestUsesFinancialDetailScreen() {
        Intent i=NotificationNavigator.requestIntent(RuntimeEnvironment.getApplication(),"r","exchanges",data("walletManaged",true),"a","account");
        assertEquals(WalletActivity.class.getName(),i.getComponent().getClassName());
        assertEquals("r",i.getStringExtra(WalletActivity.EXTRA_BOOKING_ID));
    }
}
