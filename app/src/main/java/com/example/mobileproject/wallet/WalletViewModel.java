package com.example.mobileproject.wallet;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalletViewModel extends AndroidViewModel {
    public final MutableLiveData<Integer> revision=new MutableLiveData<>(0);
    public JSONObject snapshot=new JSONObject(),quote=new JSONObject();
    public String page="HOME",message="",postId="",bookingId="",topupId="",legacyExchangeId="",draftAmount="";
    public boolean busy,online,consent;
    public boolean quoteLoading;
    public String quoteError="";
    private String uid="";private boolean initialized,loaded,active,cleared;
    private long serverMillis,receivedElapsed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final WalletCache cache;
    private final SharedPreferences prefs;
    private final List<ListenerRegistration> signals=new ArrayList<>();
    private final Runnable poll=new Runnable(){@Override public void run(){if(!active||cleared)return;refresh();main.postDelayed(this,30000);}};
    public WalletViewModel(Application app){super(app);cache=new WalletCache(app);prefs=app.getSharedPreferences("sx_wallet_operations",0);}
    public void init(String targetPostId,String exchangeId,String legacyId){
        if(initialized)return;initialized=true;
        uid=FirebaseAuth.getInstance().getUid();
        if(uid==null){uid="";message="Vui lòng đăng nhập.";changed();return;}
        postId=targetPostId==null?"":targetPostId;legacyExchangeId=legacyId==null?"":legacyId;
        bookingId=exchangeId==null?"":exchangeId;
        page=!bookingId.isEmpty()?"BOOKING":!postId.isEmpty()?"QUOTE":"HOME";
        topupId=prefs.getString(uid+":lastTopup","");
        io.execute(()->{JSONObject saved=cache.read(uid);main.post(()->{
            if(cleared||!sameUser())return;loaded=true;
            if(saved!=null&&snapshot.length()==0)snapshot=saved;
            changed();if(active){refresh();if(!postId.isEmpty())loadQuote();}
        });});
    }
    public boolean sameUser(){return !uid.isEmpty()&&uid.equals(FirebaseAuth.getInstance().getUid());}
    public String userId(){return uid;}
    public boolean usable(){return sameUser()&&online&&!busy;}
    public long serverNow(){return serverMillis==0?0:serverMillis+SystemClock.elapsedRealtime()-receivedElapsed;}
    private void changed(){if(!cleared)revision.setValue((revision.getValue()==null?0:revision.getValue())+1);}
    public void open(String next){page=next;message="";changed();}
    public void newTopup(){draftAmount="";open("TOPUP");}
    public void error(String value){message=value;changed();}
    public void start(){active=true;if(loaded){refresh();if(!postId.isEmpty()&&quote.length()==0)loadQuote();}main.removeCallbacks(poll);main.postDelayed(poll,30000);}
    public void stop(){active=false;online=false;for(ListenerRegistration r:signals)r.remove();signals.clear();main.removeCallbacks(poll);}
    private void attachSignals(){
        if(!active||!sameUser()||!signals.isEmpty())return;
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        signals.add(db.collection("wallets").document(uid).addSnapshotListener(MetadataChanges.INCLUDE,(s,e)->{if(e==null&&s!=null&&!s.getMetadata().isFromCache())refresh();}));
        signals.add(db.collection("walletTopups").whereEqualTo("userId",uid).addSnapshotListener((s,e)->{if(e==null&&s!=null&&!s.getMetadata().isFromCache())refresh();}));
        signals.add(db.collection("exchanges").whereEqualTo("requesterUid",uid).addSnapshotListener((s,e)->{if(e==null&&s!=null&&!s.getMetadata().isFromCache())refresh();}));
        signals.add(db.collection("exchanges").whereEqualTo("hostUid",uid).addSnapshotListener((s,e)->{if(e==null&&s!=null&&!s.getMetadata().isFromCache())refresh();}));
    }
    public void refresh(){
        if(!active||!loaded||cleared||busy||!sameUser())return;
        busy=true;changed();
        WalletApi.call(getApplication(),"/v1/snapshot",new JSONObject(),(data,error)->{
            if(cleared)return;busy=false;
            if(!sameUser()){snapshot=new JSONObject();online=false;message="Tài khoản đã thay đổi. Mở lại ví.";changed();return;}
            if(error!=null){online=false;message=error.getMessage();changed();return;}
            if(data.optInt("apiVersion",0)<3){online=false;message="Máy chủ đang chạy bản ví cũ. Cập nhật backend bản hủy yêu cầu nạp rồi khởi động lại.";changed();return;}
            snapshot=data;online=active;message="";serverMillis=data.optLong("serverNow");receivedElapsed=SystemClock.elapsedRealtime();
            final String owner=uid;final String serialized=data.toString();
            io.execute(()->{try{cache.save(owner,new JSONObject(serialized));}catch(Exception ignored){}});
            attachSignals();changed();
        });
    }
    public void loadQuote(){
        if(postId.isEmpty()||!sameUser()||cleared||quoteLoading)return;
        quoteLoading=true;quoteError="";quote=new JSONObject();changed();
        JSONObject body=new JSONObject();try{body.put("postId",postId);}catch(Exception ignored){ }
        WalletApi.call(getApplication(),"/v1/quote",body,(data,error)->{
            if(cleared||!sameUser())return;
            quoteLoading=false;
            if(error!=null){quoteError=error.getMessage();quote=new JSONObject();changed();return;}
            quote=data;quoteError="";changed();
        });
    }
    public JSONArray array(String key){JSONArray a=snapshot.optJSONArray(key);return a==null?new JSONArray():a;}
    public JSONObject booking(){return find(array("bookings"),"exchangeId",bookingId);}
    public JSONObject topup(){return find(array("topups"),"topupId",topupId);}
    private JSONObject find(JSONArray array,String key,String id){for(int i=0;i<array.length();i++){JSONObject o=array.optJSONObject(i);if(o!=null&&id.equals(o.optString(key)))return o;}return new JSONObject();}
    public void viewBooking(String id){bookingId=id;open("BOOKING");}
    public void viewTopup(String id){topupId=id;prefs.edit().putString(uid+":lastTopup",id).apply();open("QR");}
    private void merge(String list,String key,JSONObject value){
        JSONArray old=array(list),next=new JSONArray();next.put(value);
        for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item!=null&&!value.optString(key).equals(item.optString(key)))next.put(item);}
        try{snapshot.put(list,next);}catch(Exception ignored){ }
    }
    public void createTopup(long amount){
        JSONObject body=new JSONObject();try{body.put("amount",amount);}catch(Exception ignored){ }
        mutate("/v1/topups",body,"topup:"+amount,(data)->{topupId=data.optString("topupId");prefs.edit().putString(uid+":lastTopup",topupId).apply();merge("topups","topupId",data);page="QR";});
    }
    public void cancelTopup(String id){
        JSONObject body=new JSONObject();try{body.put("topupId",id);body.put("confirmUnpaid",true);}catch(Exception ignored){ }
        mutate("/v1/topups/cancel",body,"cancel-topup:"+id,(data)->{
            prefs.edit().remove(uid+":topup:"+data.optLong("amount")).apply();
            topupId=data.optString("topupId");merge("topups","topupId",data);page="QR";
        });
    }
    public void pay(){
        if(quoteLoading||!quoteError.isEmpty()){error("Cần tải lại thông tin phòng thành công trước khi thanh toán.");return;}
        if(!consent){error("Vui lòng đồng ý chính sách hủy trước khi thanh toán.");return;}
        if(quote.length()==0){error("Chưa tải được giá chuyến đi.");return;}
        JSONObject body=new JSONObject();try{body.put("postId",postId);body.put("expectedAmount",quote.optLong("amount"));body.put("startDate",quote.optLong("start"));body.put("endDate",quote.optLong("end"));if(!legacyExchangeId.isEmpty())body.put("exchangeId",legacyExchangeId);}catch(Exception ignored){ }
        mutate("/v1/bookings/pay",body,"pay:"+postId,(data)->{bookingId=data.optString("exchangeId");merge("bookings","exchangeId",data);page="BOOKING";});
    }
    public void action(String action){
        JSONObject body=new JSONObject();try{body.put("exchangeId",bookingId);body.put("action",action);}catch(Exception ignored){ }
        mutate("/v1/bookings/action",body,"action:"+bookingId+":"+action,(data)->{merge("bookings","exchangeId",data);page="BOOKING";});
    }
    private interface Result {void apply(JSONObject data);}
    private void mutate(String path,JSONObject body,String scope,Result result){
        if(!usable()){error("Cần kết nối trực tuyến để xác nhận giao dịch.");return;}
        String preferenceKey=uid+":"+scope;
        String operation=prefs.getString(preferenceKey,"");if(operation.isEmpty()){operation=UUID.randomUUID().toString();prefs.edit().putString(preferenceKey,operation).apply();}
        try{body.put("operationId",operation);}catch(Exception ignored){ }
        busy=true;message="";changed();
        WalletApi.call(getApplication(),path,body,(data,error)->{
            if(cleared)return;busy=false;
            if(!sameUser()){online=false;snapshot=new JSONObject();message="Tài khoản đã thay đổi.";changed();return;}
            if(error!=null){if(error.status>=400&&error.status<500)prefs.edit().remove(preferenceKey).apply();if(error.status==0||error.status>=500)online=false;message=error.getMessage();changed();return;}
            prefs.edit().remove(preferenceKey).apply();result.apply(data);changed();refresh();
        });
    }
    @Override protected void onCleared(){cleared=true;stop();main.removeCallbacksAndMessages(null);io.execute(cache::close);io.shutdown();super.onCleared();}
}
