package com.smartdoorlock;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
//임시 QR 공유 화면
public class KeyGenDialog extends Dialog {
    private Bitmap disposableQR;
    private KeyGenDialogListener listener;
    //MainActivity 로부터 QR 이미지 bitmap 받아옴
    public KeyGenDialog(@NonNull Context context, Bitmap disposableQR) {
        super(context);
        this.disposableQR = disposableQR;
    }
    //공유하기 버튼을 눌렀을 때 MainActivity 에서 공유창을 열어주기 위한 리스너
    interface KeyGenDialogListener {
        void onShare();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_key_gen);
        //받아온 QR 이미지를 ImageView 에 띄움
        ImageView ivQR = findViewById(R.id.iv_disposable_key);
        ivQR.setImageBitmap(disposableQR);
        //공유 버튼 동작
        findViewById(R.id.bt_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onShare();
                dismiss();
            }
        });

    }

    public void setListener(KeyGenDialogListener listener) {
        this.listener = listener;
    }
}


