package com.example.mobileproject.view;

import static org.junit.Assert.*;
import android.app.Application;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.wallet.WalletViewModel;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(application=Application.class,sdk=35)
public class WalletWithdrawalUiTest {
    private ActivityController<Host> controller;
    private Model model;
    private WalletWithdrawalUi ui;
    @Before public void start() throws Exception {
        controller=Robolectric.buildActivity(Host.class).setup();model=new Model(RuntimeEnvironment.getApplication());
        model.snapshot=new JSONObject().put("withdrawalVersion",1).put("isAdmin",false)
            .put("wallet",new JSONObject().put("available",100000).put("held",0));
        model.online=true;ui=new WalletWithdrawalUi(controller.get(),controller.get().content,model);
    }
    @After public void stop(){model.dispose();controller.pause().stop().destroy();}
    private void withdrawal(String state,String owner) throws Exception {
        model.withdrawalId="RUT123";
        model.snapshot.put("withdrawals",new JSONArray().put(new JSONObject().put("withdrawalId","RUT123")
            .put("amount",30000).put("bankName","Vietcombank").put("accountNumber","0123456789")
            .put("accountName","NGUYEN QUOC HUNG").put("userName","Hưng").put("status",state).put("processingAdminUid",owner)));
    }
    @Test public void requestFormPreservesDraftAndLeadingZeroDuringBalanceRefresh(){
        ui.form();EditText amount=controller.get().findViewById(R.id.withdrawalAmount),account=controller.get().findViewById(R.id.withdrawalAccount);
        amount.setText("30000");account.setText("0123456789");model.busy=true;
        assertTrue(ui.refreshForm());assertEquals("30000",model.draftWithdrawalAmount);assertEquals("0123456789",model.draftAccountNumber);
        assertSame(account,controller.get().findViewById(R.id.withdrawalAccount));assertFalse(controller.get().findViewById(R.id.withdrawalSubmit).isEnabled());
    }
    @Test public void insufficientFundsStopsBeforeSubmitting(){
        ui.form();((EditText)controller.get().findViewById(R.id.withdrawalAmount)).setText("100001");
        controller.get().findViewById(R.id.withdrawalSubmit).performClick();assertEquals(0,model.requests);assertTrue(model.message.contains("không đủ"));
    }
    @Test public void anAccountChangeClearsThePreviousBankDetailsInsteadOfPreservingTheForm(){
        ui.form();((EditText)controller.get().findViewById(R.id.withdrawalAccount)).setText("0123456789");
        model.validSession=false;assertFalse(ui.refreshForm());assertEquals(0,controller.get().content.getChildCount());
    }
    @Test public void aPendingRequestMustBeClaimedBeforeAConfirmationButtonIsAvailable() throws Exception {
        model.snapshot.put("isAdmin",true);withdrawal("PENDING","");ui.detail();
        assertNotNull(controller.get().findViewById(R.id.withdrawalClaim));assertNull(controller.get().findViewById(R.id.withdrawalPaid));
        controller.get().findViewById(R.id.withdrawalClaim).performClick();
        ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("CLAIM",model.lastAction);
    }
    @Test public void claimingAdminConfirmsWithThePaidButtonAndNoTransactionCodeInput() throws Exception {
        model.snapshot.put("isAdmin",true);withdrawal("PROCESSING","adminA");ui.detail();
        assertNotNull(controller.get().findViewById(R.id.withdrawalPaid));controller.get().findViewById(R.id.withdrawalPaid).performClick();
        Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertEquals(0,inputs(dialog.getWindow().getDecorView()));
        ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("PAID",model.lastAction);
    }
    @Test public void anotherAdminCannotConfirmOrRejectAClaimedRequest() throws Exception {
        model.snapshot.put("isAdmin",true);withdrawal("PROCESSING","adminB");ui.detail();
        assertNull(controller.get().findViewById(R.id.withdrawalPaid));assertNull(controller.get().findViewById(R.id.withdrawalReject));
    }
    @Test public void regularUsersHaveNoAdminWithdrawalButtons() throws Exception {
        withdrawal("PENDING","");ui.detail();assertNull(controller.get().findViewById(R.id.withdrawalClaim));
        assertNull(controller.get().findViewById(R.id.withdrawalPaid));assertNull(controller.get().findViewById(R.id.withdrawalReject));
    }
    @Test public void offlineOrOldServerDisablesFinancialSubmission(){
        model.online=false;ui.form();assertFalse(controller.get().findViewById(R.id.withdrawalSubmit).isEnabled());
        model.online=true;model.snapshot.remove("withdrawalVersion");assertTrue(ui.refreshForm());
        assertFalse(controller.get().findViewById(R.id.withdrawalSubmit).isEnabled());
    }
    @Test public void aPaidRequestHasNoFurtherPaymentOrRefundButton() throws Exception {
        model.snapshot.put("isAdmin",true);withdrawal("PAID","adminA");ui.detail();
        assertNull(controller.get().findViewById(R.id.withdrawalPaid));assertNull(controller.get().findViewById(R.id.withdrawalReject));
    }
    private int inputs(View view){int count=view instanceof EditText?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
        for(int i=0;i<group.getChildCount();i++)count+=inputs(group.getChildAt(i));}return count;}
    public static class Host extends AppCompatActivity {
        LinearLayout content;
        @Override protected void onCreate(Bundle state){setTheme(R.style.Theme_MobileProject);super.onCreate(state);
            content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);setContentView(content);}
    }
    private static class Model extends WalletViewModel {
        String lastAction="";int requests;boolean validSession=true;
        Model(Application app){super(app);}
        @Override public boolean sameUser(){return validSession;}
        @Override public String userId(){return "adminA";}
        @Override public boolean usable(){return online&&!busy;}
        @Override public void withdrawalAction(String action,String reason){lastAction=action;}
        @Override public void createWithdrawal(long amount){requests++;}
        void dispose(){super.onCleared();}
    }
}
