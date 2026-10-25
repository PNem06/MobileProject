package com.example.mobileproject.wallet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** No banking credentials in Android. Every financial mutation is authenticated server-side. */
public final class WalletApi {
    public interface Callback { void complete(JSONObject data, ApiException error); }
    public static class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status=status; }
    }
    private static final ExecutorService IO=Executors.newFixedThreadPool(2);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private WalletApi() { }
    public static String baseUrl(Context context) {
        return context.getSharedPreferences("sx_wallet_connection",Context.MODE_PRIVATE)
                .getString("baseUrl",WalletConfig.BASE_URL).replaceAll("/+$", "");
    }
    public static void setBaseUrl(Context context,String value) throws Exception {
        String clean=value.trim().replaceAll("/+$", ""); URL u=new URL(clean);
        if(!"https".equals(u.getProtocol()) || u.getHost().isEmpty() || u.getUserInfo()!=null ||
                u.getQuery()!=null || u.getRef()!=null || !u.getPath().isEmpty())
            throw new Exception("Nhập URL HTTPS gốc, ví dụ https://ten-cua-ban.trycloudflare.com");
        context.getSharedPreferences("sx_wallet_connection",Context.MODE_PRIVATE).edit().putString("baseUrl",clean).apply();
    }
    public static void call(Context context,String route,JSONObject body,Callback callback) {
        final String base=baseUrl(context);
        if(base.isEmpty()){callback.complete(null,new ApiException(0,"Chưa thiết lập máy chủ thanh toán. Xem hướng dẫn cấu hình."));return;}
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(user==null){callback.complete(null,new ApiException(401,"Bạn cần đăng nhập."));return;}
        final String uid=user.getUid();
        user.getIdToken(false).addOnSuccessListener(token->{
            if(token.getToken()==null){callback.complete(null,new ApiException(401,"Không lấy được phiên đăng nhập."));return;}
            IO.execute(()->{
                JSONObject result=null;ApiException failure=null;HttpsURLConnection conn=null;
                try {
                    URL url=new URL(base+route);
                    if(!"https".equals(url.getProtocol()))throw new ApiException(0,"Máy chủ phải dùng HTTPS.");
                    conn=(HttpsURLConnection)url.openConnection();
                    conn.setConnectTimeout(15000);conn.setReadTimeout(25000);conn.setInstanceFollowRedirects(false);
                    conn.setRequestMethod("POST");conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                    conn.setRequestProperty("Authorization","Bearer "+token.getToken());
                    byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(bytes.length);
                    try(java.io.OutputStream out=conn.getOutputStream()){out.write(bytes);}
                    int code=conn.getResponseCode();String raw;
                    try(InputStream in=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                        if(in==null)throw new ApiException(code,"Máy chủ không trả dữ liệu.");
                        byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(buffer.size()+n>8*1024*1024)throw new ApiException(code,"Dữ liệu phản hồi quá lớn.");buffer.write(chunk,0,n);}raw=buffer.toString("UTF-8");
                    }
                    JSONObject reply;
                    try{reply=new JSONObject(raw);}catch(Exception e){throw new ApiException(code,"URL máy chủ chưa đúng hoặc tunnel đang tắt. Kiểm tra /health.");}
                    if(code<200||code>=300||!reply.optBoolean("ok"))throw new ApiException(code,reply.optString("message","Yêu cầu chưa được xác nhận."));
                    result=reply.optJSONObject("data");if(result==null)throw new ApiException(code,"Phản hồi không hợp lệ.");
                }catch(ApiException e){failure=e;}catch(Exception e){failure=new ApiException(0,"Chưa kết nối được máy chủ. Kiểm tra mạng, backend và tunnel; không chuyển tiền lại nếu đã chuyển.");}
                finally{if(conn!=null)conn.disconnect();}
                final JSONObject data=result;final ApiException error=failure;
                MAIN.post(()->{
                    FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();
                    if(current==null||!uid.equals(current.getUid()))callback.complete(null,new ApiException(401,"Tài khoản đăng nhập đã thay đổi."));
                    else callback.complete(data,error);
                });
            });
        }).addOnFailureListener(e->callback.complete(null,new ApiException(401,"Không xác thực được đăng nhập. Đăng nhập lại rồi thử.")));
    }
}
