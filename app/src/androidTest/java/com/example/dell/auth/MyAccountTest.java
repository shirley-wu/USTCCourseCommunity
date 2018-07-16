package com.example.dell.auth;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.test.InstrumentationTestCase;
import android.util.Log;

/**
 * Created by wu-pc on 2018/7/12.
 */

public class MyAccountTest extends InstrumentationTestCase {

    final static private String TAG = "MyAccountTest";

    public void testGet() {
        Context context = InstrumentationRegistry.getContext();
        MyAccount myAccount = MyAccount.get(context);
        if(myAccount == null) {
            Log.d(TAG, "testGet: myAccount is null");
        }
        else{
            Log.d(TAG, "testGet: myAccount name = " + myAccount.getName());
            Log.d(TAG, "testGet: myAccount token = " + myAccount.getToken());
        }
    }

    public void testSet() {
        Context context = InstrumentationRegistry.getContext();
        MyAccount myAccount = MyAccount.get(context);
        Log.d(TAG, "testSet: set name = " + myAccount.setName("name"));
        Log.d(TAG, "testSet: set token " + myAccount.setToken("token"));
    }

}
