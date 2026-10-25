package com.example.mobileproject.wallet;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;

/** Offline display only; this cache never authorizes a payment or refund. */
public class WalletCache extends SQLiteOpenHelper {
    public WalletCache(Context context){super(context,"sx_wallet_cache.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE snapshots(uid TEXT PRIMARY KEY, payload TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ }
    public JSONObject read(String uid){
        try(Cursor cursor=getReadableDatabase().query("snapshots",new String[]{"payload"},"uid=?",new String[]{uid},null,null,null)){
            return cursor.moveToFirst()?new JSONObject(cursor.getString(0)):null;
        }catch(Exception e){return null;}
    }
    public void save(String uid,JSONObject json){ContentValues values=new ContentValues();values.put("uid",uid);values.put("payload",json.toString());getWritableDatabase().insertWithOnConflict("snapshots",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
}
