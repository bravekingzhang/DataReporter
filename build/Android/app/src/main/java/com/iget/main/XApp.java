package com.iget.main;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.iget.datareporter.DataReporter;
import com.tencent.matrix.Matrix;
import com.tencent.matrix.iocanary.IOCanaryPlugin;
import com.tencent.matrix.iocanary.config.IOConfig;
import com.tencent.matrix.resource.ResourcePlugin;
import com.tencent.matrix.resource.config.ResourceConfig;
import com.tencent.matrix.trace.TracePlugin;
import com.tencent.matrix.trace.config.TraceConfig;
import com.tencent.matrix.util.MatrixLog;

public class XApp extends Application {

    private static final String TAG = "Matrix.Application";

    private IntentFilter mIntentFilter;
    private NetworkChangeReceiver mNetworkChangeReceiver;
    private long mNativeReporter = 0;

    class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivity != null) {

                    NetworkInfo info = connectivity.getActiveNetworkInfo();
                    if (info != null && info.isConnected()) {

                        if (info.isConnected()) {
                            DataReporter.reaWaken(mNativeReporter);
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initReport();
    }

    private void initReport() {
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        mNetworkChangeReceiver = new NetworkChangeReceiver();
        registerReceiver(mNetworkChangeReceiver, mIntentFilter);
        if (mNativeReporter == 0) {
            final NetPost netPost = new NetPost();
            mNativeReporter = DataReporter.makeReporter("uuid", getFilesDir().getPath(), "encryptKey", netPost);
            netPost.setNativeReporter(mNativeReporter);
            DataReporter.setReportCount(mNativeReporter, 1);
            DataReporter.setFileMaxSize(mNativeReporter, 2 * 1024);
            DataReporter.setExpiredTime(mNativeReporter, 10 * 1000);
            DataReporter.setReportingInterval(mNativeReporter, 2 * 1000);
            DataReporter.start(mNativeReporter);
        }
        initMatrix();
    }

    private void initMatrix() {
        DynamicConfigImplDemo dynamicConfig = new DynamicConfigImplDemo();
        boolean matrixEnable = dynamicConfig.isMatrixEnable();
        boolean fpsEnable = dynamicConfig.isFPSEnable();
        boolean traceEnable = dynamicConfig.isTraceEnable();

        MatrixLog.i(TAG, "MatrixApplication.onCreate");

        Matrix.Builder builder = new Matrix.Builder(this);
        builder.patchListener(new IssueListener(this, mNativeReporter));

        //trace
        TraceConfig traceConfig = new TraceConfig.Builder()
                .dynamicConfig(dynamicConfig)
                .enableFPS(fpsEnable)
                .enableEvilMethodTrace(traceEnable)
                .enableAnrTrace(traceEnable)
                .enableStartup(traceEnable)
//                .splashActivities("sample.tencent.matrix.SplashActivity;")
                .isDebug(true)
                .isDevEnv(false)
                .build();

        TracePlugin tracePlugin = (new TracePlugin(traceConfig));
        builder.plugin(tracePlugin);

        if (matrixEnable) {

            //resource
            builder.plugin(new ResourcePlugin(new ResourceConfig.Builder()
                    .dynamicConfig(dynamicConfig)
                    .setDumpHprof(false)
                    .setDetectDebuger(true)     //only set true when in sample, not in your app
                    .build()));
            ResourcePlugin.activityLeakFixer(this);
            //io
            IOCanaryPlugin ioCanaryPlugin = new IOCanaryPlugin(new IOConfig.Builder()
                    .dynamicConfig(dynamicConfig)
                    .build());
            builder.plugin(ioCanaryPlugin);

        }
        Matrix.init(builder.build());
        //start only startup tracer, close other tracer.
        tracePlugin.start();
        MatrixLog.i("Matrix.HackCallback", "end:%s", System.currentTimeMillis());
    }
}
