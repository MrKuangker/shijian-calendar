package com.mrkuangker.shijian;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real Android WebView/native bridge on a disposable emulator. */
public final class SmokeInstrumentation extends Instrumentation {
    private Activity activity;
    private WebView web;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void launch() throws Exception {
        Intent intent=new Intent().setClassName(getTargetContext().getPackageName(),"com.mrkuangker.shijian.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=startActivitySync(intent);
        runOnMainSync(()->web=(WebView)((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0));
        boolean loaded=false;
        for(int i=0;i<80;i++){
            if("true".equals(evaluate("!!window.ShijianApp && document.querySelectorAll('#month-grid .day').length === 42"))){loaded=true;break;}
            Thread.sleep(200);
        }
        if(!loaded)throw new AssertionError("Embedded calendar did not load");
    }
    private String evaluate(String expression) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();
        runOnMainSync(()->web.evaluateJavascript(expression,result->{value.set(result);latch.countDown();}));
        if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("WebView bridge timeout");
        return value.get();
    }
    private void require(String expression, String message) throws Exception {
        if(!"true".equals(evaluate(expression)))throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle results=new Bundle();
        try {
            launch();
            require("location.origin === 'https://app.shijian.local'","Untrusted embedded origin");
            evaluate("document.getElementById('add-event').click(); document.getElementById('event-title').value='Android smoke record'; document.getElementById('event-content').value='Native bridge persistence check'; document.getElementById('event-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); true");
            require("JSON.parse(ShijianAndroid.loadData()).events.some(e=>e.title==='Android smoke record')","Native record was not saved");
            require("document.querySelector('#day-events .event-title').textContent === 'Android smoke record'","Saved record missing from UI");
            runOnMainSync(()->activity.finish());waitForIdleSync();launch();
            require("document.querySelector('#day-events .event-title').textContent === 'Android smoke record'","Record did not survive Activity restart");
            evaluate("document.querySelector('#day-events [data-action=complete]').click();true");
            require("JSON.parse(ShijianAndroid.loadData()).events[0].completed === true","Completion was not persisted");
            require("JSON.parse(ShijianAndroid.appInfo()).versionCode > 0","App version metadata missing");
            PackageInfo info=getTargetContext().getPackageManager().getPackageArchiveInfo(getTargetContext().getApplicationInfo().sourceDir,PackageManager.GET_SIGNING_CERTIFICATES);
            if(info==null||info.signingInfo==null||info.signingInfo.getApkContentsSigners().length!=1)throw new AssertionError("APK signature metadata missing");
            results.putString("stream","\nSHIJIAN_SMOKE_PASS\nCalendar, native storage, Activity restart, completion and APK signature metadata verified.\n");
            finish(Activity.RESULT_OK,results);
        } catch(Throwable error) {
            results.putString("stream","\nSHIJIAN_SMOKE_FAIL: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,results);
        }
    }
}
