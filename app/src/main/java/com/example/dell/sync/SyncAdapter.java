package com.example.dell.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.SyncStats;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.example.dell.auth.MyAccount;
import com.example.dell.auth.ServerAuthenticator;
import com.example.dell.db.DatabaseHelper;
import com.example.dell.db.Picture;
import com.example.dell.diary.DiaryWriteActivity;
import com.example.dell.server.ServerAccessor;
import com.j256.ormlite.cipher.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Created by wu-pc on 2018/6/3.
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SyncAdapter";

    private static final String PREFERENCE_NAME = "SYNC ANCHOR";

    private Context mContext;

    private SharedPreferences sharedPreferences;

    /**
     * Set up the sync adapter
     */
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        sharedPreferences = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;
    }

    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "onPerformSync: begin");

        DatabaseHelper helper = OpenHelperManager.getHelper(mContext, DatabaseHelper.class);

        MyAccount myAccount = MyAccount.get(mContext);
        if (myAccount == null) {
            Log.e(TAG, "onPerformSync: cannot get my account");
            return ;
        }

        boolean verified = ServerAuthenticator.veriy(myAccount.getName(), myAccount.getToken());
        Log.d(TAG, "onPerformSync: verified = " + verified);
        if (!verified) {
            Bundle bundle = new Bundle();
            boolean status = ServerAuthenticator.signIn(myAccount.getName(), myAccount.getPassword(), bundle);
            if (status && bundle.getBoolean("success")) {
                myAccount.setToken(bundle.getString("token"));
                myAccount.save();
            }
            else {
                myAccount.setPassword(null);
                myAccount.setToken(null);
                myAccount.save();
                return ;
            }
        }

        boolean status;
        status = sync(helper);
        Log.d(TAG, "onPerformSync: 同步数据库 status = " + status);
        status = syncPic(helper);
        Log.d(TAG, "onPerformSync: 同步图片 status = " + status);

        OpenHelperManager.releaseHelper();

        Log.d(TAG, "onPerformSync: end");
    }

    public boolean sync(DatabaseHelper databaseHelper) {
        try {
            Class[] tableList = databaseHelper.getTableList();
            Map<Class, List> classListMap = new Hashtable<>();

            StringBuilder dataBuilder = new StringBuilder("{");

            for(int i=0; ; ) {
                Class clazz = tableList[i];
                QueryBuilder queryBuilder = databaseHelper.getDaoAccess(clazz).queryBuilder();
                queryBuilder.where().lt("status", 9);
                List list = queryBuilder.query();

                classListMap.put(clazz, list);

                dataBuilder.append(
                        clazz.getSimpleName() + "List\":" + JSON.toJSONString(list, SerializerFeature.DisableCircularReferenceDetect)
                );
                i++;
                if (i < tableList.length) dataBuilder.append(",");
                else break;
            }

            dataBuilder.append("}");

            String data = dataBuilder.toString();
            Log.d(TAG, "sync: data = " + data);

            HttpResponse httpResponse = postSyncData(data);

            int responseCode = httpResponse.getStatusLine().getStatusCode();
            Log.d(TAG, "sync: response code = " + responseCode);

            Header[] headers = httpResponse.getHeaders("anchor");
            if(headers.length != 1) return false;

            long anchor = Long.parseLong(headers[0].getValue());
            Log.d(TAG, "sync: anchor = " + anchor);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("anchor", anchor);
            editor.commit();

            HttpEntity entity = httpResponse.getEntity();
            String response = EntityUtils.toString(entity, "utf-8");
            Log.d(TAG, "sync: response = " + response);

            if (responseCode != 200) return false;

            // 获取json object，进行处理
            JSONObject jsonObject = JSON.parseObject(response);
            for(Class clazz : tableList) {
                Log.d(TAG, "sync: clazz = " + clazz);

                // 获得需要的Dao
                Dao dao = databaseHelper.getDaoAccess(clazz);

                // 通过反射，获取getStatus方法
                Method method = clazz.getDeclaredMethod("getStatus");

                // 删除list中需要删除的部分
                List list = classListMap.get(clazz);
                for (Object o : list) {
                    try {
                        int status = (int) method.invoke(o);
                        if (status == -1) {
                            int code = dao.delete(o);
                            Log.d(TAG, "sync: 删除 " + o + " 返回值 = " + code);
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "sync: ", e);
                    }
                }

                // 获取返回的list
                JSONArray jsonArray = jsonObject.getJSONArray(clazz.getSimpleName() + "List");
                Log.d(TAG, "sync: json array = " + jsonArray.toString());
                list = jsonArray.toJavaList(clazz);
                for (Object o : list) {
                    try {
                        int status = (int) method.invoke(o);
                        if (status == -1) {
                            // 删除
                            int code = dao.delete(o);
                            Log.d(TAG, "sync: 删除 " + o + " 返回值 = " + code);
                        }
                        if (status != -1) {
                            // 更新
                            Dao.CreateOrUpdateStatus code = dao.createOrUpdate(o);
                            Log.d(TAG, "sync: 插入或更新 返回值 = 插入" + code.isCreated() + " 更新" + code.isUpdated());
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "sync: ", e);
                    }
                }

            }

            return true;
        } catch(Exception e) {
            Log.e(TAG, "sync: ", e);
            return false;
        }
    }

    public boolean syncPic(DatabaseHelper databaseHelper) {
        try {
            long anchor = sharedPreferences.getLong("anchor", -1);

            QueryBuilder<Picture, Long> queryBuilder = databaseHelper.getDaoAccess(Picture.class).queryBuilder();
            queryBuilder.where().gt("modified", anchor);
            List<Picture> list = queryBuilder.query();

            for(Picture picture : list) {
                String path = DiaryWriteActivity.SD_PATH + "image_" + picture.getId() + ".jpg";
                // TODO: ???
            }

            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "syncPic: ", e);
            return false;
        }
    }

    public HttpResponse postSyncData(String sendData) {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(httpParams, 60000);
        HttpClient httpClient = new DefaultHttpClient(httpParams);

        String url = ServerAccessor.getServerIp() + ":8080/HeartTrace_Server_war/Servlet.Sync1";
        Log.d(TAG, "postSyncData: url " + url);
        HttpPost httpPost = new HttpPost(url);

        ArrayList<NameValuePair> pairs = new ArrayList<>();

        pairs.add(new BasicNameValuePair("modelnum", Build.MODEL));

        MyAccount myAccount = MyAccount.get(mContext);
        pairs.add(new BasicNameValuePair("username", myAccount.getName()));
        pairs.add(new BasicNameValuePair("token", myAccount.getToken()));

        pairs.add(new BasicNameValuePair("content", sendData));

        Long anchor = sharedPreferences.getLong("anchor", -1);
        Log.d(TAG, "postSyncData: anchor = " + anchor);
        pairs.add(new BasicNameValuePair("anchor", anchor.toString()));

        try {
            HttpEntity requestEntity = new UrlEncodedFormEntity(pairs);
            Log.d(TAG, "postSyncData: request entity = " + EntityUtils.toString(requestEntity, "utf-8"));
            httpPost.setEntity(requestEntity);

            HttpResponse httpResponse = httpClient.execute(httpPost);
            return httpResponse;
        }
        catch (Exception e) {
            Log.e(TAG, "postSyncData: ", e);
        }

        return null;
    }

}