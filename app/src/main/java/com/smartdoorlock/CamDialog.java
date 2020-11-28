package com.smartdoorlock;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

public class CamDialog extends Dialog {
    private String DESTINATION;


    public CamDialog(@NonNull Context context, String destination) {
        super(context);
        this.DESTINATION = destination;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_cam);
        // 웹뷰 선언
        final WebView mWebView = findViewById(R.id.wb_cam);
        mWebView.setWebViewClient(new WebViewClient()); // 클릭시 새창 안뜨게
        //웹뷰세팅
        WebSettings mWebSettings = mWebView.getSettings(); //세부 세팅 등록
        mWebSettings.setJavaScriptEnabled(true); // 웹페이지 자바스크립트 허용 여부
        mWebSettings.setSupportMultipleWindows(false); // 새창 띄우기 허용 여부
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(false); // 자바스크립트 새창 띄우기(멀티뷰) 허용 여부
        mWebSettings.setLoadWithOverviewMode(true); // 메타태그 허용 여부
        mWebSettings.setUseWideViewPort(true); // 화면 사이즈 맞추기 허용 여부
        mWebSettings.setSupportZoom(false); // 화면 줌 허용 여부
        mWebSettings.setBuiltInZoomControls(false); // 화면 확대 축소 허용 여부
        mWebSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 브라우저 캐시 허용 여부
        mWebSettings.setDomStorageEnabled(true); // 로컬저장소 허용 여부
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.loadUrl(String.format("http://%s:8080", DESTINATION)); // 웹뷰에 표시할 라즈베리파이 주소, 웹뷰 시작

        findViewById(R.id.bt_dialog_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mWebView.destroy();
                dismiss();
            }
        });

    }


}
