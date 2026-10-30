package com.example.mobileproject.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import com.example.mobileproject.R;
import com.example.mobileproject.wallet.WalletViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Withdrawal content lives inside the existing wallet screen and keeps its shared navigation. */
final class WalletWithdrawalUi {
    private final AppCompatActivity host;
    private final LinearLayout content;
    private final WalletViewModel vm;
    private final Typeface bodyFont, headingFont;
    private MaterialButton submit;
    private TextView formBalance;

    WalletWithdrawalUi(AppCompatActivity host, LinearLayout content, WalletViewModel vm) {
        this.host=host;this.content=content;this.vm=vm;
        bodyFont=ResourcesCompat.getFont(host,R.font.sx_noto_sans);
        headingFont=ResourcesCompat.getFont(host,R.font.sx_fraunces);
    }
    private int dp(int n){return Math.round(n*host.getResources().getDisplayMetrics().density);}
    private String money(long n){return NumberFormat.getIntegerInstance(new Locale("vi","VN")).format(n)+"đ";}
    private String date(long n){SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("vi","VN"));f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));return n>0?f.format(new Date(n)):"Chưa cập nhật";}
    private TextView text(LinearLayout parent,String value,int size,boolean title){
        TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(Color.parseColor("#242135"));
        t.setTypeface(title?headingFont:bodyFont);t.setPadding(0,dp(5),0,dp(5));parent.addView(t);return t;
    }
    private LinearLayout card(){
        LinearLayout box=new LinearLayout(host);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(17),dp(13),dp(17),dp(13));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(17));box.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);content.addView(box,p);return box;
    }
    private MaterialButton button(LinearLayout parent,String label,boolean enabled,int id,Runnable click){
        MaterialButton b=new MaterialButton(host);b.setText(label);b.setAllCaps(false);b.setTypeface(headingFont);b.setTextSize(15);
        b.setCornerRadius(dp(25));b.setMinHeight(dp(48));b.setEnabled(enabled);b.setAlpha(enabled?1f:.5f);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#211C55")));
        if(id!=0)b.setId(id);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);parent.addView(b,p);
        b.setOnClickListener(v->click.run());return b;
    }
    private interface Draft {void set(String value);}
    private void input(LinearLayout box,String label,String value,int type,int id,Draft save){
        text(box,label,14,true);EditText field=new EditText(host);field.setId(id);field.setTextSize(16);field.setTypeface(bodyFont);
        field.setInputType(type);field.setSingleLine(true);field.setText(value);box.addView(field,new LinearLayout.LayoutParams(-1,-2));
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){save.set(s.toString());}public void afterTextChanged(Editable e){}});
    }
    static String status(String state){
        switch(state){case "PENDING":return "Chờ quản trị xử lý";case "PROCESSING":return "Đang chuyển khoản";
            case "PAID":return "Đã chuyển khoản";case "REJECTED":return "Đã từ chối và trả lại tiền giữ";default:return "Đang tải trạng thái";}
    }
    private boolean ready(){return vm.usable()&&vm.withdrawalsSupported();}
    private long available(){JSONObject w=vm.snapshot.optJSONObject("wallet");return w==null?0:w.optLong("available");}
    boolean refreshForm(){
        if(!vm.sameUser()){content.removeAllViews();submit=null;formBalance=null;return false;}
        if(submit==null||formBalance==null)return false;formBalance.setText("Số dư khả dụng: "+money(available()));submit.setEnabled(ready());submit.setAlpha(ready()?1f:.5f);return true;
    }
    void form(){
        text(content,"Rút tiền về ngân hàng",25,true);
        formBalance=text(content,"Số dư khả dụng: "+money(available()),15,false);
        text(content,"Số tiền được giữ ngay khi gửi yêu cầu. Tổng tiền trong ví chỉ giảm khi quản trị xác nhận đã chuyển khoản.",13,false);
        LinearLayout box=card();
        input(box,"Số tiền muốn rút (VND)",vm.draftWithdrawalAmount,InputType.TYPE_CLASS_NUMBER,R.id.withdrawalAmount,v->vm.draftWithdrawalAmount=v);
        input(box,"Ngân hàng nhận",vm.draftBankName,InputType.TYPE_CLASS_TEXT,R.id.withdrawalBank,v->vm.draftBankName=v);
        input(box,"Số tài khoản nhận",vm.draftAccountNumber,InputType.TYPE_CLASS_NUMBER,R.id.withdrawalAccount,v->vm.draftAccountNumber=v);
        input(box,"Tên chủ tài khoản",vm.draftAccountName,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,R.id.withdrawalName,v->vm.draftAccountName=v);
        submit=button(content,"Gửi yêu cầu rút tiền",ready(),R.id.withdrawalSubmit,this::confirmRequest);
        button(content,"Xem yêu cầu rút của tôi",true,0,()->vm.open("WITHDRAWALS"));
        if(!vm.withdrawalsSupported())text(content,"Chức năng rút tiền chưa sẵn sàng. Vui lòng liên hệ quản trị.",13,false);
    }
    private void confirmRequest(){
        long amount;
        try{amount=Long.parseLong(vm.draftWithdrawalAmount.trim());if(amount<1000||amount>50000000)throw new Exception();}
        catch(Exception e){vm.error("Nhập số tiền từ 1.000đ đến 50.000.000đ.");return;}
        if(amount>available()){vm.error("Số dư khả dụng không đủ để rút.");return;}
        if(vm.draftBankName.trim().isEmpty()||!vm.draftAccountNumber.trim().matches("[0-9]{5,30}")||vm.draftAccountName.trim().isEmpty()){
            vm.error("Nhập ngân hàng, số tài khoản và tên người nhận hợp lệ.");return;
        }
        new MaterialAlertDialogBuilder(host).setTitle("Xác nhận yêu cầu rút")
            .setMessage(money(amount)+"\n"+vm.draftBankName.trim()+" · "+vm.draftAccountNumber.trim()+"\n"+vm.draftAccountName.trim()
                +"\n\nHãy kiểm tra đúng tài khoản nhận. Số tiền này sẽ được giữ để chờ quản trị xử lý.")
            .setPositiveButton("Gửi yêu cầu",(d,w)->vm.createWithdrawal(amount)).setNegativeButton("Kiểm tra lại",null).show();
    }
    void list(){
        boolean admin=vm.snapshot.optBoolean("isAdmin");text(content,admin?"Yêu cầu rút tiền":"Yêu cầu rút của tôi",25,true);
        if(!vm.withdrawalsSupported()){text(content,"Chức năng rút tiền chưa sẵn sàng. Vui lòng liên hệ quản trị.",14,false);return;}
        if(admin)text(content,"Đang chờ chi: "+money(vm.snapshot.optLong("withdrawalPendingAmount"))+" · "+vm.snapshot.optInt("withdrawalPendingCount")+" yêu cầu. Khoản này đang giữ trên ví người dùng.",14,false);
        else button(content,"Tạo yêu cầu rút tiền",ready(),0,vm::newWithdrawal);
        JSONArray rows=vm.array("withdrawals");if(rows.length()==0)text(content,"Chưa có yêu cầu rút tiền.",14,false);
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;LinearLayout box=card();
            text(box,money(row.optLong("amount")),23,true);text(box,status(row.optString("status")),15,true);
            if(admin)text(box,row.optString("userName","Thành viên"),14,false);
            text(box,row.optString("bankName")+" · "+date(row.optLong("createdAt")),13,false);
            String id=row.optString("withdrawalId");button(box,"Xem chi tiết",true,0,()->vm.viewWithdrawal(id));
        }
    }
    void detail(){
        JSONObject row=vm.withdrawal();text(content,"Chi tiết rút tiền",25,true);
        if(row.length()==0){text(content,"Đang tải yêu cầu. Hãy làm mới trạng thái khi có kết nối.",14,false);return;}
        LinearLayout box=card();text(box,money(row.optLong("amount")),28,true);text(box,status(row.optString("status")),16,true);
        text(box,"Mã yêu cầu: "+row.optString("withdrawalId"),13,false);
        text(box,"Người yêu cầu: "+row.optString("userName","Thành viên"),14,false);
        text(box,"Ngân hàng: "+row.optString("bankName"),14,false);text(box,"Số tài khoản: "+row.optString("accountNumber"),16,true);
        text(box,"Chủ tài khoản: "+row.optString("accountName"),14,false);text(box,"Gửi lúc: "+date(row.optLong("createdAt")),13,false);
        String state=row.optString("status");boolean admin=vm.snapshot.optBoolean("isAdmin");
        if("PROCESSING".equals(state))text(box,"Đang xử lý: "+row.optString("processingAdminName","Quản trị"),14,false);
        if("PAID".equals(state))text(box,"Quản trị xác nhận đã chuyển khoản lúc "+date(row.optLong("paidAt"))+". Khoản giữ đã được chốt trừ.",14,false);
        if("REJECTED".equals(state))text(box,"Lý do: "+row.optString("rejectionReason")+"\nTiền đã được trả lại số dư khả dụng.",14,false);
        if(admin&&"PENDING".equals(state)){
            text(content,"Nhận xử lý thành công trước khi chuyển tiền để tránh hai quản trị viên cùng chuyển khoản.",14,false);
            button(content,"Nhận xử lý",ready(),R.id.withdrawalClaim,()->new MaterialAlertDialogBuilder(host)
                .setTitle("Nhận xử lý yêu cầu?").setMessage("Sau khi nhận thành công, bạn là người thực hiện chuyển khoản và xác nhận yêu cầu này.")
                .setPositiveButton("Nhận xử lý",(d,w)->vm.withdrawalAction("CLAIM","")).setNegativeButton("Quay lại",null).show());
        }
        boolean own=admin&&"PROCESSING".equals(state)&&vm.userId().equals(row.optString("processingAdminUid"));
        if(own){
            text(content,"Chuyển khoản thủ công đúng tài khoản ở trên. Chỉ bấm Đã chuyển khoản sau khi ngân hàng báo thành công; nếu kết nối lỗi, kiểm tra trạng thái trước khi chuyển lại.",14,false);
            button(content,"Sao chép số tài khoản",true,0,()->copy(row.optString("accountNumber")));
            button(content,"Sao chép số tiền",true,0,()->copy(String.valueOf(row.optLong("amount"))));
            button(content,"Đã chuyển khoản",ready(),R.id.withdrawalPaid,()->new MaterialAlertDialogBuilder(host)
                .setTitle("Xác nhận đã chuyển khoản?").setMessage("Bạn đã chuyển thành công "+money(row.optLong("amount"))+" đến "+row.optString("accountNumber")+"? Hệ thống sẽ chốt trừ khoản tiền đang giữ.")
                .setPositiveButton("Đã chuyển khoản",(d,w)->vm.withdrawalAction("PAID","")).setNegativeButton("Chưa chuyển",null).show());
        }
        if(admin&&("PENDING".equals(state)||own))button(content,"Từ chối · Chưa chuyển khoản",ready(),R.id.withdrawalReject,this::reject);
        if(!admin&&("PENDING".equals(state)||"PROCESSING".equals(state)))text(content,"Số tiền đang được giữ để chờ chuyển khoản. Nếu yêu cầu bị từ chối trước khi chuyển, tiền sẽ trở lại số dư khả dụng.",14,false);
        button(content,"Về danh sách rút tiền",true,0,()->vm.open("WITHDRAWALS"));
    }
    private void copy(String value){ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Thông tin rút tiền",value));}
    private void reject(){
        EditText reason=new EditText(host);reason.setSingleLine(true);reason.setHint("Lý do từ chối (3–300 ký tự)");
        new MaterialAlertDialogBuilder(host).setTitle("Từ chối khi chưa chuyển khoản")
            .setMessage("Xác nhận bạn chưa chuyển tiền. Số tiền giữ sẽ được trả lại ví người dùng.").setView(reason)
            .setPositiveButton("Từ chối và trả tiền giữ",(d,w)->vm.withdrawalAction("REJECT",reason.getText().toString()))
            .setNegativeButton("Quay lại",null).show();
    }
}
