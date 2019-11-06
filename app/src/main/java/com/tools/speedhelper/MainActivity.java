package com.tools.speedhelper;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.base.lib.baseui.AppBaseActivity;
import com.base.lib.util.AbStrUtil;
import com.base.lib.util.DeviceUtils;
import com.lxj.okhttpdownloader.download.DownloadEngine;
import com.lxj.okhttpdownloader.download.DownloadInfo;
import com.lxj.okhttpdownloader.download.L;
import com.tools.speedhelper.service.SocketService;
import com.tools.speedhelper.util.Util;
import com.tools.speedhelper.widget.SineWave;
import com.tools.speedlib.SpeedManager;
import com.tools.speedlib.listener.NetDelayListener;
import com.tools.speedlib.listener.SpeedListener;
import com.tools.speedlib.utils.ConverUtil;
import com.tools.speedlib.views.AwesomeSpeedView;

import java.io.File;

public class MainActivity extends AppBaseActivity {
    private AwesomeSpeedView speedometer;
    private TextView downloadText,downloadUnitText,uploadText,uploadUnitText,delayText;
    private LinearLayout startLayout;
    private SineWave downloadWave,uploadWave;
    SpeedManager speedManager;
    private long firstTime;
    private int clickCount = 0;
    private String delayTime;
    private String downloadPath = Environment.getExternalStorageDirectory()+"/Video/";

    private String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private DownloadInfo dInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speedometer = findViewById(R.id.speedometer);

        downloadText = findViewById(R.id.download_speed_text);
        downloadUnitText = findViewById(R.id.download_unit_text);
        uploadText = findViewById(R.id.upload_speed_text);
        uploadUnitText = findViewById(R.id.upload_unit_text);
        delayText = findViewById(R.id.delay_text);
        downloadWave = findViewById(R.id.speed_download_view);
        uploadWave = findViewById(R.id.speed_upload_view);
        startLayout = findViewById(R.id.start_layout);

        findViewById(R.id.start_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                start();
                startDownload();
            }
        });
        startService();

        checkPermissions(permissions, 1, new PermissionsResultListener() {
            @Override
            public void onSuccessful(int[] results) {

            }
            @Override
            public void onFailure() {

            }
        });
    }
    /**
     * 启动服务
     */
    public void startService(){
        Intent intent = new Intent(this, SocketService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
    /**
     * 跳转至设置
     */
    public void gotoSetting(View view){
        clickCount++;
        if((System.currentTimeMillis()-firstTime) > 3000)  //System.currentTimeMillis()无论何时调用，肯定大于2000
        {
            firstTime = System.currentTimeMillis();
            clickCount = 0;
        }else{
            if(clickCount==1){
                clickCount = 0;
                startActivity(new Intent(this,SettingActivity.class));
            }
        }
    }
    private void start(){
        startLayout.setVisibility(View.GONE);
        downloadWave.Set(Util.centerEndX);
        uploadWave.Set(Util.centerEndY);
        SocketService.getVRService().sendMessageToServer("start");
        speedManager = new SpeedManager.Builder()
                .setNetDelayListener(new NetDelayListener() {
                    @Override
                    public void result(String delay) {
                        delayTime = AbStrUtil.formatDouble(Double.valueOf(delay),0);
                        delayText.setText(delayTime);
                    }
                })
                .setSpeedListener(new SpeedListener() {
                    @Override
                    public void onStart() {
                        downloadWave.clearData();
                        uploadWave.clearData();
                    }
                    @Override
                    public void speeding(long downSpeed, long upSpeed) {
                        setSpeedText(downSpeed,upSpeed);
                    }
                    @Override
                    public void finishSpeed(long finalDownSpeed, long finalUpSpeed) {
                        setSpeedText(finalDownSpeed,finalUpSpeed);
                        startLayout.setVisibility(View.VISIBLE);
                    }
                })
                .setPindCmd("59.61.92.196")
                .setSpeedCount(100)
                .setSpeedTimeOut(2000)
                .builder();
        speedManager.startSpeed();
    }
    private void startDownload(){
        String taskId = String.valueOf(System.currentTimeMillis());
        final String videoPath = downloadPath+taskId+".mp4";
        //需要自己维护任务id
        DownloadEngine.create(this).download(taskId, "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4", videoPath);
        DownloadEngine.create(this).setMaxTaskCount(5);
        //添加
        DownloadEngine.create(this).addDownloadObserver(new DownloadEngine.DownloadObserver() {
            @Override
            public void onDownloadUpdate(DownloadInfo downloadInfo) {
                dInfo = downloadInfo;
                long speed = caculateSpeed(downloadInfo.currentLength);
                L.d("the speed ="+speed+"kb/s,state="+downloadInfo.state);
                if(speed<1000){
                    speedometer.setCurrentSpeed(ConverUtil.roundByScale(speed,2));
                    speedometer.setUnit("kb/s");
                }else {
                    speedometer.setCurrentSpeed(ConverUtil.roundByScale(speed/1024,2));
                    speedometer.setUnit("m/s");
                }
//            speedometer.speedPercentTo(ConverUtil.getSpeedPercent(speed));
               // speedometer.speedPercentTo((int) (downSpeed*100/1000));
                if(downloadInfo.state==3){//下载完成，打开文件
                    long curTime = System.currentTimeMillis();
                    int usedTime = (int) ((curTime-startTime)/1000);
                    L.d("文件大小为："+downloadInfo.size/1024+"下载完成...耗费..."+usedTime+"s");
                    playVideo(videoPath);
                }
            }
        },taskId);
        startTime = System.currentTimeMillis();
//移除
      //  DownloadEngine.create(this).removeDownloadObserver(this);
    }
    long startTime = System.currentTimeMillis(); // 开始下载时获取开始时间
    private long caculateSpeed(long downloadSize){
        long curTime = System.currentTimeMillis();
        int usedTime = (int) ((curTime-startTime)/1000);

        if(usedTime==0)usedTime = 1;
        return (downloadSize/usedTime)/1024; // 下载速度
    }
    /**
     * 调用系统播放器
     * @param videoUrl
     */
    private void playVideo(String videoUrl){
        String extension = MimeTypeMap.getFileExtensionFromUrl(videoUrl);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        Intent mediaIntent = new Intent(Intent.ACTION_VIEW);
        mediaIntent.setDataAndType(Uri.parse(videoUrl), mimeType);
        startActivity(mediaIntent);
    }
    /**
     * 设置上传下载速度
     * @param downSpeed
     * @param upSpeed
     */
    private void setSpeedText(long downSpeed, long upSpeed){
        String[] downResult = ConverUtil.formatSpeed(downSpeed);
        double dSpeed = Double.valueOf(downResult[0]);
//        if(dSpeed<800){//保证速率800以上
//            dSpeed = dSpeed+(8-(int) dSpeed/100)*100;
//        }
        double uSpeed = dSpeed/4;
        String downText = ConverUtil.roundByScale(dSpeed,2);
        String upText = ConverUtil.roundByScale(uSpeed,2);
        downloadText.setText(downText);
        downloadUnitText.setText(downResult[1]);
        setSpeedView(downSpeed,dSpeed,downResult);

      //  String[] upResult = ConverUtil.formatSpeed(upSpeed);
        uploadText.setText(upText);
        uploadUnitText.setText(downResult[1]);
        downloadWave.Set((int)dSpeed);
        uploadWave.Set((int)uSpeed);

        SocketService.getVRService().sendMessageToServer("DL="+downText+downResult[1]+",UP="+upText+downResult[1]+",Ping="+delayTime+"ms");
    }
    private void setSpeedView(long speed,double downSpeed, String[] result) {
        if (null != result && 2 == result.length) {
            speedometer.setCurrentSpeed(ConverUtil.roundByScale(downSpeed,2));
            speedometer.setUnit(result[1]);
//            speedometer.speedPercentTo(ConverUtil.getSpeedPercent(speed));
            speedometer.speedPercentTo((int) (downSpeed*100/1000));
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(speedManager!=null){
            speedManager.finishSpeed();
        }
        if(dInfo!=null){
            DownloadEngine.create(this).deleteDownloadInfo(dInfo);
        }
    }
}
