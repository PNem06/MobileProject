package com.example.mobileproject.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.example.mobileproject.wallet.WalletApi;
import com.example.mobileproject.wallet.WalletViewModel;
import com.example.mobileproject.util.PostImageUtils;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android wallet, top-up and rental screens, backed by trusted HTTPS API. */
public class WalletActivity extends AppCompatActivity {
    public static final String EXTRA_BOOKING_ID="walletBookingId";
    public static final String EXTRA_LEGACY_ID="walletLegacyExchangeId";
    private WalletViewModel vm;
    private LinearLayout content;
    private Typeface headingFont,bodyFont;
    private final Handler clock=new Handler(Looper.getMainLooper());
    private final ExecutorService imageIo=Executors.newSingleThreadExecutor();
    private CustomTarget<Bitmap> imageDownload;
    private TextView countdown;
    private MaterialButton cancelButton;
    private long cancelUntil;
    private String lastRenderedPage="";
    private String lastRenderedBank="";
    private MaterialButton topupCreate;
    private final Map<String,String> roomPhotos=new HashMap<>();
    private final Runnable tick=new Runnable(){@Override public void run(){updateCountdown();clock.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);EdgeToEdge.enable(this);setContentView(R.layout.activity_wallet);
        headingFont=ResourcesCompat.getFont(this,R.font.sx_fraunces);bodyFont=ResourcesCompat.getFont(this,R.font.sx_noto_sans);
        content=findViewById(R.id.walletContent);vm=new ViewModelProvider(this).get(WalletViewModel.class);
        findViewById(R.id.walletBack).setOnClickListener(v->{if("HOME".equals(vm.page)||"QUOTE".equals(vm.page))finish();else vm.open("HOME");});
        findViewById(R.id.walletBell).setOnClickListener(v->vm.open("NOTICES"));
        findViewById(R.id.walletBell).setOnLongClickListener(v->{if(debugBuild()){configure();return true;}return false;});
        setupNav();
        com.example.mobileproject.navigation.MainNavigationInsets.install(findViewById(R.id.walletRoot),R.id.walletBottomNav);
        vm.revision.observe(this,revision->render());
        vm.init(getIntent().getStringExtra("targetPostId"),getIntent().getStringExtra(EXTRA_BOOKING_ID),getIntent().getStringExtra(EXTRA_LEGACY_ID));
    }
    private boolean debugBuild(){return (getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout.LayoutParams params(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private GradientDrawable bg(String color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(radius));return d;}
    private TextView text(LinearLayout parent,String value,int size,String color,boolean title){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.parseColor(color));t.setTypeface(title?headingFont:bodyFont);t.setPadding(0,dp(4),0,dp(4));parent.addView(t,params(-1,-2));return t;}
    private void heading(String value){text(content,value,25,"#111122",true);}
    private void sub(String value){text(content,value,13,"#6D6975",false);}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(17),dp(15),dp(17),dp(15));c.setBackground(bg("#FFFFFF",17));c.setElevation(dp(1));LinearLayout.LayoutParams p=params(-1,-2);p.topMargin=dp(15);content.addView(c,p);return c;}
    private void row(LinearLayout parent,String label,String value){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);TextView l=new TextView(this),v=new TextView(this);l.setText(label);v.setText(value);l.setTextColor(Color.parseColor("#6D6975"));v.setTextColor(Color.parseColor("#151325"));l.setTextSize(13);v.setTextSize(14);l.setTypeface(bodyFont);v.setTypeface(bodyFont);v.setGravity(Gravity.END);r.addView(l,new LinearLayout.LayoutParams(0,-2,1));r.addView(v,new LinearLayout.LayoutParams(0,-2,1.2f));r.setPadding(0,dp(9),0,dp(9));parent.addView(r,params(-1,-2));}
    private MaterialButton button(LinearLayout parent,String label,boolean primary,boolean enabled,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(label);b.setTextSize(15);b.setTypeface(headingFont);b.setAllCaps(false);b.setCornerRadius(dp(25));b.setMinHeight(dp(48));b.setTextColor(Color.parseColor(primary?"#FFFFFF":"#C92233"));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(primary?"#FF3037":"#E4E2E6")));b.setEnabled(enabled);b.setAlpha(enabled?1f:.5f);LinearLayout.LayoutParams p=params(-1,-2);p.topMargin=dp(9);parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private void note(String value,boolean green){LinearLayout c=card();c.setBackground(bg(green?"#EAF8EF":"#F0EDFA",13));text(c,value,13,green?"#176A45":"#433883",false);}
    private String money(long amount){return NumberFormat.getIntegerInstance(new Locale("vi","VN")).format(amount)+"đ";}
    private String date(long time){if(time<=0)return "Chưa cập nhật";SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("vi","VN"));f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));return f.format(new Date(time));}
    private String status(String raw){switch(raw){case "PENDING":return "Đang chờ phản hồi";case "ACCEPTED":return "Đã chấp nhận";case "ONGOING":return "Đang diễn ra";case "CANCELLED":return "A đã hủy";case "REJECTED":return "B đã từ chối";case "COMPLETED":return "Đã hoàn tất";default:return raw;}}
    private void setupNav(){
        BottomNavigationView nav=findViewById(R.id.walletBottomNav);
        nav.getMenu().findItem(R.id.menu_settings).setChecked(true);
        nav.setOnItemSelectedListener(item->{
            com.example.mobileproject.navigation.MainTab tab=com.example.mobileproject.navigation.MainTab.fromMenuId(item.getItemId());
            if(tab==null)return false;
            startActivity(MainActivity.tabIntent(this,tab));
            finish();
            return true;
        });
    }
    private void render(){
        if(vm==null||content==null)return;
        if(vm.snapshot.optBoolean("isAdmin")&&("TOPUP".equals(vm.page)||"QR".equals(vm.page)||"QUOTE".equals(vm.page)))vm.page="HOME";
        String title;
        switch(vm.page){case "TOPUP":case "QR":title="Nạp tiền";break;case "HISTORY":title="Lịch sử giao dịch";break;case "NOTICES":title="Thông báo";break;case "QUOTE":title="Thuê phòng";break;case "BOOKING":title="Chi tiết trao đổi";break;default:title=vm.snapshot.length()==0?"Ví StayExchange":vm.snapshot.optBoolean("isAdmin")?"Ví quản trị":"Ví của tôi";}
        ((TextView)findViewById(R.id.walletTitle)).setText(title);
        findViewById(R.id.walletLoading).setVisibility(vm.busy?View.VISIBLE:View.INVISIBLE);
        ((TextView)findViewById(R.id.walletConnection)).setText(vm.online?"Đã đồng bộ với hệ thống":"Dữ liệu lưu để xem lại · Cần mạng để giao dịch");
        TextView error=findViewById(R.id.walletError);error.setText(vm.message);error.setVisibility(vm.message.isEmpty()?View.GONE:View.VISIBLE);
        String bankConfiguration=currentBank().optString("configurationId")+"|"+waitingTopup().optString("topupId");
        if ("TOPUP".equals(vm.page) && "TOPUP".equals(lastRenderedPage) && lastRenderedBank.equals(bankConfiguration) && topupCreate != null && content.getChildCount() > 0) { topupCreate.setEnabled(vm.usable()); topupCreate.setAlpha(vm.usable()?1f:.5f); return; }
        lastRenderedPage=vm.page;
        lastRenderedBank=bankConfiguration;
        content.removeAllViews();countdown=null;cancelButton=null;cancelUntil=0;
        if(!vm.sameUser()){heading("Ví StayExchange");sub("Vui lòng đăng nhập đúng tài khoản và mở lại ví.");return;}
        if(WalletApi.baseUrl(this).isEmpty()){heading("Kết nối thanh toán");sub("Nhóm cần thiết lập địa chỉ máy chủ một lần trước khi sử dụng ví.");if(debugBuild())button(content,"Thiết lập kết nối",true,true,this::configure);return;}
        switch(vm.page){case "TOPUP":renderTopup();break;case "QR":renderQr();break;case "QUOTE":renderQuote();break;case "BOOKING":renderBooking();break;case "HISTORY":renderHistory();break;case "NOTICES":renderNotices();break;default:renderHome();}
        if(!"TOPUP".equals(vm.page))button(content,"Làm mới trạng thái",false,!vm.busy,()->{vm.refresh();if("QUOTE".equals(vm.page))vm.loadQuote();});
        if(debugBuild()&&!vm.online)button(content,"Cấu hình máy chủ của nhóm",false,true,this::configure);
        updateCountdown();
    }
    private void renderHome(){
        if(vm.snapshot.length()==0){heading("Đang tải dữ liệu ví");sub("Kết nối máy chủ để xác nhận ví và số dư của phiên đăng nhập hiện tại.");return;}
        boolean admin=vm.snapshot.optBoolean("isAdmin");sub((admin?"Đang đăng nhập: ":"")+vm.snapshot.optString("name","Thành viên StayExchange"));
        JSONObject w=vm.snapshot.optJSONObject("wallet");if(w==null)w=new JSONObject();long available=w.optLong("available"),held=w.optLong("held");
        LinearLayout balance=card();balance.setBackground(getDrawable(R.drawable.bg_black_blue));text(balance,admin?"Tổng số dư ví hệ thống":"Số dư khả dụng",14,"#E4E0FF",false);text(balance,money(admin?available+held:available),35,"#FFFFFF",true);
        if(admin){text(balance,"Đang giữ hộ: "+money(held),15,"#FFFFFF",false);text(balance,"Khả dụng (gồm phí hủy): "+money(available),15,"#FFFFFF",false);}else button(balance,"Nạp tiền vào ví",true,vm.usable(),vm::newTopup);
        if(admin)note("Các quản trị viên cùng xem một ví hệ thống, gồm tiền giữ hộ và phần khả dụng. Khoản giữ gắn với từng yêu cầu; hệ thống tự hoàn cho A hoặc trả cho B theo trạng thái giao dịch.",false);
        JSONObject waiting=waitingTopup();if(waiting.length()>0){final String id=waiting.optString("topupId");button(content,"Mở yêu cầu nạp đang chờ · "+money(waiting.optLong("amount")),false,true,()->vm.viewTopup(id));}
        for(int i=0;i<vm.array("topups").length();i++){JSONObject t=vm.array("topups").optJSONObject(i);if(t!=null&&"REVIEW".equals(t.optString("status"))){final String id=t.optString("topupId");button(content,"Khoản nạp cần đối soát · "+money(t.optLong("amount")),false,true,()->vm.viewTopup(id));break;}}
        button(content,"Lịch sử giao dịch",false,true,()->vm.open("HISTORY"));
        text(content,admin?"Các yêu cầu đang theo dõi":"Yêu cầu thuê phòng của bạn",19,"#141222",true);
        JSONArray bookings=vm.array("bookings");if(bookings.length()==0)sub("Chưa có yêu cầu thanh toán bằng ví. Vào bài đăng → Đặt phòng → Thuê phòng để bắt đầu.");
        for(int i=0;i<bookings.length();i++){JSONObject b=bookings.optJSONObject(i);if(b==null)continue;LinearLayout c=card();text(c,b.optString("postTitle"),17,"#141222",true);row(c,status(b.optString("status")),money(b.optLong("amount")));subCard(c,date(b.optLong("startDate"))+" → "+date(b.optLong("endDate")));final String id=b.optString("exchangeId");button(c,"Xem chi tiết",false,true,()->vm.viewBooking(id));}
        if(admin&&vm.array("bankEvents").length()>0){text(content,"Đối soát khoản nạp",19,"#141222",true);for(int i=0;i<vm.array("bankEvents").length();i++){JSONObject e=vm.array("bankEvents").optJSONObject(i);if(e==null||"PAID".equals(e.optString("result")))continue;LinearLayout c=card();text(c,e.optString("result")+" · "+money(e.optLong("amount")),15,"#A13D25",true);subCard(c,"Giao dịch SePay: "+e.optString("id")+" · "+e.optString("topupId","Không khớp nội dung"));}}
    }
    private void subCard(LinearLayout parent,String value){text(parent,value,12,"#6D6975",false);}
    private JSONObject currentBank(){JSONObject b=vm.snapshot.optJSONObject("bank");return b==null?new JSONObject():b;}
    private boolean topupCurrent(JSONObject topup){
        JSONObject b=currentBank();String id=topup.optString("bankConfigId");
        if(!id.isEmpty())return id.equals(b.optString("configurationId"));
        return topup.optString("bankCode",topup.optString("bank")).equalsIgnoreCase(b.optString("code"))
            &&topup.optString("accountNumber").equals(b.optString("accountNumber"));
    }
    private JSONObject waitingTopup(){
        for(int i=0;i<vm.array("topups").length();i++){
            JSONObject t=vm.array("topups").optJSONObject(i);
            if(t!=null&&"PENDING".equals(t.optString("status"))&&topupCurrent(t)&&t.optLong("expiresAt")>vm.serverNow())return t;
        }
        return new JSONObject();
    }
    private String topupState(JSONObject t){
        String state=t.optString("status");
        if("PAID".equals(state))return "Đã nạp tiền";
        if("CANCELLED".equals(state))return "Đã hủy yêu cầu";
        if("REVIEW".equals(state))return "Cần đối soát";
        if("PENDING".equals(state)&&vm.serverNow()>0&&t.optLong("expiresAt")<=vm.serverNow())return "Đã hết hạn";
        return "Đang chờ chuyển khoản";
    }
    private void renderTopup(){
        heading("Nạp tiền vào ví");sub("Chuyển khoản "+currentBank().optString("name","ngân hàng của quản trị")+" · Tự động cộng tiền khi đối chiếu thành công");
        LinearLayout c=card();text(c,"Số tiền muốn nạp (VND)",15,"#141222",true);EditText input=new EditText(this);input.setTextSize(27);input.setTypeface(headingFont);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);input.setHint("Nhập số tiền");input.setText(vm.draftAmount);c.addView(input,params(-1,-2));input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){vm.draftAmount=s.toString();}public void afterTextChanged(Editable e){}});
        LinearLayout quick=new LinearLayout(this);quick.setOrientation(LinearLayout.HORIZONTAL);c.addView(quick,params(-1,-2));for(long amount:new long[]{10000,50000,100000}){MaterialButton b=new MaterialButton(this);b.setText(money(amount));b.setAllCaps(false);b.setTextSize(12);b.setMaxLines(1);b.setMinWidth(0);b.setPadding(dp(3),dp(8),dp(3),dp(8));b.setCornerRadius(dp(20));quick.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->input.setText(String.valueOf(amount)));}
        note("Chuyển vào tài khoản ngân hàng của quản trị hiển thị ở bước tiếp theo. Số tiền sẽ được cộng vào đúng ví theo mã yêu cầu nạp, không cần admin duyệt tay.",false);
        JSONObject waiting=waitingTopup();
        if(waiting.length()>0){
            final JSONObject pending=waiting;
            LinearLayout existing=card();text(existing,"Bạn có yêu cầu đang chờ",16,"#141222",true);
            row(existing,"Số tiền yêu cầu",money(pending.optLong("amount")));
            subCard(existing,"Nếu muốn đổi số tiền và chưa chuyển tiền, hủy yêu cầu này trước.");
            button(existing,"Mở yêu cầu đang chờ",false,true,()->vm.viewTopup(pending.optString("topupId")));
            button(existing,"Hủy yêu cầu đang chờ",false,vm.usable(),()->confirmCancelTopup(pending));
        }
        topupCreate=button(content,"Tạo yêu cầu nạp tiền",true,vm.usable(),()->{try{long amount=Long.parseLong(vm.draftAmount);if(amount<1000||amount>50000000)throw new Exception();vm.createTopup(amount);}catch(Exception e){vm.error("Nhập số tiền từ 1.000đ đến 50.000.000đ.");}});
        button(content,"Về ví của tôi",false,true,()->vm.open("HOME"));
        button(content,"Kiểm tra kết nối",false,true,vm::refresh);
    }
    private void renderQr(){
        JSONObject t=vm.topup();heading("Nạp tiền qua "+t.optString("bank",currentBank().optString("name","ngân hàng")));
        if(t.length()==0){sub("Đang tải yêu cầu nạp. Bấm làm mới nếu chưa thấy thông tin.");return;}
        String state=t.optString("status");
        LinearLayout details=card();row(details,"Mã nạp",t.optString("topupId"));row(details,"Số tiền yêu cầu",money(t.optLong("amount")));row(details,"Trạng thái",topupState(t));
        button(details,"Sao chép mã nạp",false,true,()->copy(t.optString("topupId")));
        if("PAID".equals(state)){
            note("✓ Nạp tiền thành công. Hệ thống đã cộng "+money(t.optLong("amount"))+" vào ví.",true);
            row(details,"Thời điểm nhận",date(t.optLong("paidAt")));button(content,"Về ví",true,true,()->vm.open("HOME"));
            if(!vm.postId.isEmpty())button(content,"Tiếp tục thuê phòng",false,true,()->{vm.open("QUOTE");vm.loadQuote();});return;
        }
        if("CANCELLED".equals(state)){
            note("Yêu cầu nạp đã hủy. Hãy tạo yêu cầu mới với số tiền đúng nếu muốn nạp tiếp.",false);
            row(details,"Đã hủy lúc",date(t.optLong("cancelledAt")));
            button(content,"Tạo yêu cầu nạp mới",true,vm.usable(),vm::newTopup);
            button(content,"Về ví",false,true,()->vm.open("HOME"));return;
        }
        if("REVIEW".equals(state)){
            note("Khoản nạp cần đối soát. Không chuyển thêm tiền. Liên hệ quản trị kèm mã "+t.optString("topupId")+".",false);
            sub(t.optString("reviewReason","Cần kiểm tra số tiền, nội dung và thời điểm chuyển khoản."));
            if(t.optLong("reviewAmount")>0)row(details,"Ngân hàng báo",money(t.optLong("reviewAmount")));
            if(t.optLong("reviewBankTime")>0)row(details,"Chuyển khoản lúc",date(t.optLong("reviewBankTime")));
            button(content,"Về ví",false,true,()->vm.open("HOME"));return;
        }
        if(!"PENDING".equals(state)){note("Trạng thái yêu cầu cần được kiểm tra. Hãy làm mới hoặc liên hệ quản trị kèm mã nạp.",false);return;}
        if(!vm.online){note("Cần kết nối máy chủ để kiểm tra tài khoản nhận tiền trước khi hiển thị QR. Nếu đã chuyển, hãy làm mới trạng thái khi có mạng.",false);return;}
        if(!topupCurrent(t)){
            note("Ngân hàng hoặc tài khoản nhận tiền đã thay đổi. Yêu cầu này thuộc cấu hình cũ; nếu đã chuyển tiền, liên hệ quản trị để đối soát trước khi nạp tiếp.",false);
            button(content,"Hủy yêu cầu nạp",false,vm.usable(),()->confirmCancelTopup(t));return;
        }
        boolean expired=vm.serverNow()>0&&vm.serverNow()>=t.optLong("expiresAt");
        note(expired?"Yêu cầu đã hết hạn. Nếu đã chuyển, hãy kiểm tra trạng thái và liên hệ quản trị; không chuyển lại.":"Tạo QR chưa cộng tiền vào ví. Ví tự cập nhật khi nhận được khoản chuyển đúng số tiền và mã nạp.",false);
        button(content,"Hủy yêu cầu nạp",false,vm.usable(),()->confirmCancelTopup(t));
        LinearLayout c=card();row(c,"Ngân hàng",t.optString("bank"));row(c,"Chủ tài khoản",t.optString("accountName"));row(c,"Số nhận tiền",t.optString("accountNumber"));row(c,"Số tiền",money(t.optLong("amount")));row(c,"Nội dung",t.optString("content"));row(c,"Hạn chuyển tiền",date(t.optLong("expiresAt")));
        if(!expired){ImageView qr=new ImageView(this);qr.setAdjustViewBounds(true);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setContentDescription("QR nạp tiền "+t.optString("bank"));LinearLayout.LayoutParams p=params(240,240);p.gravity=Gravity.CENTER_HORIZONTAL;c.addView(qr,p);Glide.with(this).load(t.optString("qrUrl")).into(qr);button(content,"Lưu QR vào thư viện ảnh",false,true,()->saveQr(t.optString("qrUrl"),t.optString("topupId")));String bankCode=t.optString("bankCode",t.optString("bank"));button(content,"Mở ứng dụng "+("VPBank".equalsIgnoreCase(bankCode)?"VPBank NEO":"MBBank"),true,true,()->openBank(bankCode));}
        button(content,"Sao chép nội dung chuyển khoản",false,true,()->copy(t.optString("content")));
        button(content,"Sao chép số tài khoản nhận",false,true,()->copy(t.optString("accountNumber")));
        sub("Giữ nguyên nội dung và số tiền. Có thể quét bằng ngân hàng khác hỗ trợ VietQR hoặc lưu QR rồi chọn ảnh trong ứng dụng ngân hàng.");
        if(expired)button(content,"Tạo yêu cầu nạp mới",false,vm.usable(),this::startAnotherTopup);
    }
    private void confirmCancelTopup(JSONObject topup){
        final String id=topup.optString("topupId");
        new MaterialAlertDialogBuilder(this).setTitle("Hủy yêu cầu nạp tiền?")
            .setMessage("Yêu cầu: "+id+"\nSố tiền: "+money(topup.optLong("amount"))+"\n\nChỉ hủy nếu bạn chưa chuyển tiền. Nếu đã chuyển, chọn Quay lại và làm mới trạng thái để đối soát.")
            .setPositiveButton("Chưa chuyển tiền, hủy",(d,w)->vm.cancelTopup(id))
            .setNegativeButton("Quay lại",null).show();
    }
    private void startAnotherTopup(){new MaterialAlertDialogBuilder(this).setTitle("Bạn đã chuyển tiền chưa?").setMessage("Nếu đã chuyển, hãy đối soát trước khi tạo yêu cầu mới. Không nạp lại chỉ vì chưa thấy số dư.").setPositiveButton("Chưa chuyển, tạo mới",(d,w)->vm.newTopup()).setNegativeButton("Quay lại",null).show();}
    private void renderQuote(){
        heading("Thuê phòng");sub("Thanh toán bằng ví StayExchange");JSONObject q=vm.quote;
        if(q.length()==0){
            if(vm.quoteLoading)sub("Đang tải thông tin giá và chuyến đi…");
            else if(!vm.quoteError.isEmpty()){
                note(vm.quoteError,false);
                sub("Kiểm tra bài đăng đã được duyệt, chủ phòng và ngày bắt đầu/kết thúc. Phòng đã qua ngày bắt đầu cần cập nhật lịch hoặc chọn bài khác.");
            }else sub("Chưa tải được thông tin thanh toán của phòng.");
            button(content,"Tải lại thông tin phòng",true,!vm.quoteLoading,vm::loadQuote);
            button(content,"Mở ví của tôi",false,true,()->vm.open("HOME"));
            return;
        }
        LinearLayout trip=card();postImage(trip,q.optString("postImageUrl"),q.optString("postId"));text(trip,q.optString("title"),19,"#141222",true);subCard(trip,q.optString("address"));row(trip,"Chủ nhà",q.optString("hostName"));row(trip,"Ngày bắt đầu",date(q.optLong("start")));row(trip,"Ngày kết thúc",date(q.optLong("end")));row(trip,"Giá cho cả chuyến",money(q.optLong("amount")));
        JSONObject wallet=vm.snapshot.optJSONObject("wallet");long available=wallet==null?0:wallet.optLong("available"),amount=q.optLong("amount");
        LinearLayout summary=card();row(summary,"Số dư khả dụng",money(available));row(summary,"Số dư sau thanh toán",available>=amount?money(available-amount):"Chưa đủ số dư");
        note("B từ chối: hoàn 100%. A hủy trước khi đủ 24 giờ từ lúc thanh toán: hoàn 80%, phí hủy 20%, kể cả B đã chấp nhận. Sau 24 giờ A không được hủy.",false);
        sub("Tiền được giữ tại ví admin và tự trả cho B khi chuyến đi kết thúc, sau khi đã hết thời hạn hủy của A.");
        CheckBox consent=new CheckBox(this);consent.setText("Tôi đồng ý chính sách hủy và giữ tiền.");consent.setTextColor(Color.parseColor("#514D5B"));consent.setTextSize(13);consent.setChecked(vm.consent);content.addView(consent,params(-1,-2));
        MaterialButton pay=button(content,"Xác nhận thanh toán "+money(amount),true,vm.usable()&&available>=amount&&vm.consent,vm::pay);
        consent.setOnCheckedChangeListener((v,checked)->{vm.consent=checked;pay.setEnabled(vm.usable()&&available>=amount&&checked);pay.setAlpha(pay.isEnabled()?1f:.5f);});
        if(available<amount){final long needed=Math.max(1000,amount-available);button(content,"Nạp "+money(needed)+" bằng QR",false,vm.usable(),()->{vm.draftAmount=String.valueOf(needed);vm.open("TOPUP");});}
        button(content,"Xem ví và yêu cầu của tôi",false,true,()->vm.open("HOME"));
    }
    private void renderBooking(){
        JSONObject b=vm.booking();heading("Chi tiết yêu cầu");if(b.length()==0){sub("Chưa tải được giao dịch này. Bấm làm mới hoặc mở lại từ danh sách ví.");return;}
        boolean payer=vm.userId().equals(b.optString("requesterUid")),host=vm.userId().equals(b.optString("hostUid"));String state=b.optString("status");long amount=b.optLong("amount");
        LinearLayout c=card();postImage(c,b.optString("postImageUrl"),b.optString("postId"));text(c,b.optString("postTitle"),19,"#141222",true);row(c,"Trạng thái",status(state));row(c,"Mã yêu cầu",b.optString("exchangeId"));row(c,"Đã thanh toán",money(amount));row(c,"Thanh toán lúc",date(b.optLong("paidAt")));row(c,"Ngày bắt đầu",date(b.optLong("startDate")));row(c,"Ngày kết thúc",date(b.optLong("endDate")));
        if("HELD".equals(b.optString("settlementState"))){
            note("Đã thanh toán bằng ví. Admin đang giữ "+money(amount)+" cho yêu cầu này.",false);
            LinearLayout details=card();row(details,"Hạn hủy của A",date(b.optLong("cancelUntil")));row(details,"Thời điểm dự kiến trả B",date(b.optLong("releaseAt")));countdown=text(details,"",14,"#8A5B22",false);cancelUntil=b.optLong("cancelUntil");
            if(host&&"PENDING".equals(state)){button(content,"Chấp nhận yêu cầu",true,vm.usable(),()->confirmAction("ACCEPT",b));button(content,"Từ chối · Hoàn đủ tiền cho A",false,vm.usable(),()->confirmAction("REJECT",b));}
            if(payer)cancelButton=button(content,"Hủy yêu cầu · Hoàn 80%",false,vm.usable()&&vm.serverNow()>0&&vm.serverNow()<cancelUntil,()->confirmAction("CANCEL",b));
            if("ACCEPTED".equals(state)||"ONGOING".equals(state))note("B đã chấp nhận. Hệ thống sẽ tự trả tiền vào ví B khi đến thời điểm hoàn tất.",true);
        }else if("COMPLETED".equals(state)){note("✓ Chuyến đi đã hoàn tất. "+money(amount)+" đã được trả vào ví B.",true);}
        else if("CANCELLED".equals(state)||"REJECTED".equals(state)){LinearLayout refund=card();row(refund,"Đã hoàn về ví A",money(b.optLong("refundedAmount")));row(refund,"Phí hủy của admin",money(b.optLong("cancellationFee")));row(refund,"Thời điểm hoàn",date(b.optLong("refundedAt")));}
        if(payer||host)button(content,payer?"Nhắn tin với chủ nhà":"Nhắn tin với khách",false,true,()->{
            Intent intent=new Intent(this,ChatDetailActivity.class);intent.putExtra(ChatDetailActivity.EXTRA_RECIPIENT_ID,b.optString(payer?"hostUid":"requesterUid"));startActivity(intent);
        });
        button(content,"Về ví của tôi",false,true,()->vm.open("HOME"));
    }
    private void confirmAction(String action,JSONObject booking){
        long amount=booking.optLong("amount"),fee=Math.round(amount*.2);String title,body;
        if("CANCEL".equals(action)){title="Hủy yêu cầu trong 24 giờ?";body="Đã thanh toán: "+money(amount)+"\nPhí hủy 20%: "+money(fee)+"\nHoàn về ví A: "+money(amount-fee)+"\n\nThời hạn được kiểm tra lại trên server khi xác nhận.";}
        else if("REJECT".equals(action)){title="Từ chối yêu cầu?";body="Hệ thống hoàn đủ "+money(amount)+" vào ví A ngay khi từ chối thành công.";}
        else{title="Chấp nhận yêu cầu?";body="Hệ thống thông báo cho A và admin. Tiền tiếp tục được giữ đến khi chuyến đi hoàn tất.";}
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body).setPositiveButton("Xác nhận",(d,w)->vm.action(action)).setNegativeButton("Quay lại",null).show();
    }
    private void renderHistory(){heading("Lịch sử giao dịch");JSONArray entries=vm.array("ledger");if(entries.length()==0)sub("Chưa có giao dịch.");for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;LinearLayout c=card();text(c,e.optString("title"),17,"#141222",true);long amount=e.optLong("amount");text(c,(amount>0?"+":"")+money(amount),23,amount>0?"#087A52":"#C92233",true);subCard(c,("held".equals(e.optString("pocket"))?"Khoản giữ":"Số dư khả dụng")+" · "+date(e.optLong("createdAt")));String id=e.optString("exchangeId");if(!id.isEmpty())button(c,"Xem yêu cầu",false,true,()->vm.viewBooking(id));String topup=e.optString("topupId");if(!topup.isEmpty())button(c,"Xem khoản nạp",false,true,()->vm.viewTopup(topup));}
        text(content,"Yêu cầu nạp tiền",19,"#141222",true);
        JSONArray topups=vm.array("topups");if(topups.length()==0)sub("Chưa có yêu cầu nạp.");
        for(int i=0;i<topups.length();i++){
            JSONObject topup=topups.optJSONObject(i);if(topup==null)continue;
            LinearLayout c=card();row(c,money(topup.optLong("amount")),topupState(topup));
            subCard(c,topup.optString("topupId"));subCard(c,date(topup.optLong("createdAt")));
            final String id=topup.optString("topupId");button(c,"Xem yêu cầu nạp",false,true,()->vm.viewTopup(id));
        }
    }
    private void renderNotices(){heading("Thông báo ví");JSONArray entries=vm.array("notifications");if(entries.length()==0)sub("Chưa có thông báo ví.");for(int i=0;i<entries.length();i++){JSONObject n=entries.optJSONObject(i);if(n==null)continue;LinearLayout c=card();text(c,n.optString("title"),17,"#141222",true);subCard(c,n.optString("body"));subCard(c,date(n.optLong("createdAt")));String id=n.optString("exchangeId");if(!id.isEmpty())button(c,"Xem yêu cầu",false,true,()->vm.viewBooking(id));}}
    private void postImage(LinearLayout parent,String url,String postId){
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Ảnh chỗ ở");image.setBackgroundColor(Color.parseColor("#EFEDF8"));image.setImageResource(R.drawable.ic_home);parent.addView(image,params(-1,140));
        if(!url.isEmpty()){showPhoto(image,url);return;}
        if(postId.isEmpty())return;
        if(roomPhotos.containsKey(postId)){showPhoto(image,roomPhotos.get(postId));return;}
        loadPhoto(image,postId,"images",true);
    }
    private void loadPhoto(ImageView image,String postId,String collection,boolean tryLegacy){
        // Keep the group's URL/Base64 image storage; do not copy large images into wallet records.
        FirebaseFirestore.getInstance().collection(collection).whereEqualTo("postId",postId).limit(5).get()
            .addOnSuccessListener(rows->{
                if(isFinishing()||isDestroyed())return;
                String photo="";
                for(DocumentSnapshot doc:rows.getDocuments()){
                    String value=doc.getString("imgUrl");if(value==null||value.trim().isEmpty())value=doc.getString("imageUrl");
                    if(value!=null&&!value.trim().isEmpty()){photo=value;break;}
                }
                if(photo.isEmpty()&&tryLegacy){loadPhoto(image,postId,"postImages",false);return;}
                roomPhotos.put(postId,photo);showPhoto(image,photo);
            }).addOnFailureListener(error->{if(!isFinishing()&&!isDestroyed()&&tryLegacy)loadPhoto(image,postId,"postImages",false);});
    }
    private void showPhoto(ImageView image,String photo){if(isFinishing()||isDestroyed())return;Object model=PostImageUtils.asGlideModel(photo);if(model!=null)Glide.with(this).load(model).placeholder(R.drawable.ic_home).into(image);}
    private void updateCountdown(){if(vm==null||countdown==null)return;long now=vm.serverNow();if(now<=0){countdown.setText("Cần kết nối để kiểm tra thời hạn hủy.");return;}long left=cancelUntil-now;if(left<=0){countdown.setText("A đã hết thời hạn hủy 24 giờ.");if(cancelButton!=null){cancelButton.setEnabled(false);cancelButton.setAlpha(.5f);}}else{long seconds=left/1000;countdown.setText(String.format(Locale.US,"Còn %02d:%02d:%02d để A hủy (phí 20%%)",seconds/3600,(seconds%3600)/60,seconds%60));}}
    private void configure(){EditText input=new EditText(this);input.setText(WalletApi.baseUrl(this));input.setHint("https://ten-cua-ban.trycloudflare.com");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setPadding(dp(18),dp(12),dp(18),dp(12));new MaterialAlertDialogBuilder(this).setTitle("Máy chủ thanh toán của nhóm").setMessage("Dán URL HTTPS gốc do tunnel của nhóm cung cấp, không thêm /health.").setView(input).setPositiveButton("Lưu kết nối",(d,w)->{try{WalletApi.setBaseUrl(this,input.getText().toString());vm.refresh();if(!vm.postId.isEmpty())vm.loadQuote();}catch(Exception e){vm.error(e.getMessage());}}).setNegativeButton("Quay lại",null).show();}
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("StayExchange",value));Toast.makeText(this,"Đã sao chép",Toast.LENGTH_SHORT).show();}
    private void openBank(String code){String packageName;if("MBBank".equalsIgnoreCase(code)||"MB".equalsIgnoreCase(code))packageName="com.mbmobile";else if("VPBank".equalsIgnoreCase(code))packageName="com.vnpay.vpbankonline";else{Toast.makeText(this,"Vui lòng mở ứng dụng ngân hàng thủ công.",Toast.LENGTH_LONG).show();return;}Intent bank=getPackageManager().getLaunchIntentForPackage(packageName);if(bank==null){Toast.makeText(this,"Mở ứng dụng ngân hàng của bạn và chọn ảnh QR vừa lưu.",Toast.LENGTH_LONG).show();return;}try{startActivity(bank);}catch(Exception e){Toast.makeText(this,"Vui lòng mở ứng dụng ngân hàng thủ công.",Toast.LENGTH_LONG).show();}}
    private void saveQr(String url,String id){
        if(imageDownload!=null)Glide.with(this).clear(imageDownload);
        imageDownload=new CustomTarget<Bitmap>(){
            @Override public void onResourceReady(Bitmap resource,Transition<? super Bitmap> transition){if(isDestroyed()||isFinishing())return;Bitmap copy=resource.copy(Bitmap.Config.ARGB_8888,false);imageIo.execute(()->{Uri uri=null;String result;try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"StayExchange_"+id+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/StayExchange");values.put(MediaStore.Images.Media.IS_PENDING,1);uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new Exception();try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!copy.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception();}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);result="Đã lưu QR. Chọn ảnh này trong ứng dụng ngân hàng để chuyển khoản.";}catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);result="Không lưu được QR. Kiểm tra kết nối và bộ nhớ.";}finally{copy.recycle();}final String message=result;runOnUiThread(()->{if(!isDestroyed())Toast.makeText(WalletActivity.this,message,Toast.LENGTH_LONG).show();});});}
            @Override public void onLoadCleared(Drawable placeholder) { }
            @Override public void onLoadFailed(Drawable error){Toast.makeText(WalletActivity.this,"Không tải được QR. Kiểm tra mạng rồi thử lại.",Toast.LENGTH_LONG).show();}
        };Glide.with(this).asBitmap().load(url).into(imageDownload);
    }
    @Override protected void onStart(){super.onStart();vm.start();clock.post(tick);}
    @Override protected void onStop(){clock.removeCallbacks(tick);vm.stop();super.onStop();}
    @Override protected void onDestroy(){if(imageDownload!=null)Glide.with(getApplicationContext()).clear(imageDownload);imageIo.shutdown();super.onDestroy();}
}
