package com.example.dell.diary;

import android.content.Intent;
import android.os.Looper;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dell.auth.ServerAuthenticator;

public class RegisterActivity extends AppCompatActivity {

    Button bt_Reg;
    TextInputLayout username_layout;
    TextInputLayout password_layout;
    TextInputLayout password_layout2;
    EditText username;
    EditText password;
    EditText password2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bt_Reg = (Button) findViewById(R.id.btn_Reg);
        username_layout = (TextInputLayout) findViewById(R.id.username_wrapper);
        password_layout = (TextInputLayout) findViewById(R.id.password_wrapper);
        password_layout2 = (TextInputLayout) findViewById(R.id.password_wrapper2);
        username = (EditText) findViewById(R.id.username);
        password = (EditText) findViewById(R.id.password);
        password2 = (EditText) findViewById(R.id.password2);

        password_layout.setPasswordVisibilityToggleEnabled(true);
        password_layout2.setPasswordVisibilityToggleEnabled(true);

        bt_Reg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = username.getText().toString();
                final String pw1 = password.getText().toString();
                final String pw2 = password2.getText().toString();
                if (validateAccount(name) && validatePassword1(pw1) && validatePassword2( pw1,pw2)) {
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            Bundle bundle = new Bundle();
                            boolean status = ServerAuthenticator.signUp(name, pw1, bundle);
                            if(status == false) {
                                // 接口错误或者网络错误
                                Looper.prepare();
                                try {
                                    Toast.makeText(RegisterActivity.this, "接口错误或者网络错误", Toast.LENGTH_SHORT).show();
                                }catch (Exception e) {
                                    Log.e("error",e.toString());
                                }
                                Looper.loop();
                            }
                            else {
                                boolean success = bundle.getBoolean("success");
                                String msg = bundle.getString("msg");
                                // success表示操作成功与否；msg表示服务器返回信息
                                if(success){
                                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                    intent.putExtra("origin", "register");
                                    intent.putExtra("name",name);
                                    intent.putExtra("password",pw1);
                                    startActivity(intent);
                                    finish();

                                    Looper.prepare();
                                    try {
                                        Toast.makeText(RegisterActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                                    }catch (Exception e) {
                                        Log.e("error",e.toString());
                                    }
                                    Looper.loop();

                                }
                                else{
                                    Looper.prepare();
                                    try {
                                        Toast.makeText(RegisterActivity.this, "注册失败，该用户名已被使用", Toast.LENGTH_SHORT).show();
                                    }catch (Exception e) {
                                        Log.e("error",e.toString());
                                    }
                                    Looper.loop();

                                }
                            }
                        }
                    }).start();

                }
            }
        });

    }
    private boolean validateAccount(String name) {
        //username_layout.setErrorEnabled(true);
        if (name.isEmpty()) {
            username_layout.setError("用户名不能为空");
            return false;
        } else {
            username_layout.setErrorEnabled(false);
        }
        return true;
    }

    private boolean validatePassword1(String pw) {
        //password_layout.setErrorEnabled(true);
        if (pw.isEmpty()) {
            password_layout.setError("密码不能为空");
            return false;
        }
        else if (pw.length() < 6 || pw.length() > 18) {
            password_layout.setError("密码长度6-18位");
            return false;
        } else {
            password_layout.setErrorEnabled(false);
        }
        return true;
    }

    private boolean validatePassword2(String pw1,String pw2) {
        //password_layout.setErrorEnabled(true);
        if (!pw1.equals(pw2)) {
            password_layout2.setError("两次输入密码不一致");
            return false;
        }
         else {
            password_layout2.setErrorEnabled(false);
        }
        return true;
    }
}