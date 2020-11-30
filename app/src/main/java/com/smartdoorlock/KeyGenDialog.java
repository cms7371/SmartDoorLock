package com.smartdoorlock;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Bundle;
import android.text.method.KeyListener;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import net.glxn.qrgen.android.QRCode;

import java.nio.Buffer;

public class KeyGenDialog extends Dialog {
    private Bitmap disposableQR;
    private KeyGenDialogListener listener;

    public KeyGenDialog(@NonNull Context context, Bitmap disposableQR) {
        super(context);
        this.disposableQR = disposableQR;
    }

    interface KeyGenDialogListener{
        void onShare();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_key_gen);
        ImageView ivQR = findViewById(R.id.iv_disposable_key);
        ivQR.setImageBitmap(disposableQR);
        findViewById(R.id.bt_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onShare();
                dismiss();
//                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
//                sharingIntent.putExtra("QR", disposableQR);
            }
        });

    }

    public void setListener(KeyGenDialogListener listener){
        this.listener = listener;
    }
}
