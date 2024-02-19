package com.zebra.wsc_exerciser;

import static android.widget.Toast.makeText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
//https://developer.android.com/reference/android/hardware/display/DisplayManager#registerDisplayListener(android.hardware.display.DisplayManager.DisplayListener,%20android.os.Handler)

import com.zebra.valueadd.IZVAService;

public class HDLauncherActivity extends AppCompatActivity  implements ServiceConnection {

    Intent starterIntent;
    private com.zebra.valueadd.IZVAService iServiceBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        starterIntent = getIntent();

        setContentView(R.layout.activity_hdlauncher);
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {  //As a multi-screen-aware app, detect a 2nd screen when available
                //move this launcher to the 2nd screen when available
                finish();
                ActivityOptions ao =ActivityOptions.makeBasic();
                ao.setLaunchDisplayId(displayId);
                Bundle bao = ao.toBundle();
                starterIntent.setFlags(  Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(starterIntent, bao);
            }

            @Override
            public void onDisplayRemoved(int displayId) {

            }

            @Override
            public void onDisplayChanged(int displayId) {
                recreate();
            }
        }, null);


    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void onClickbtn_DisplaySettings(View v) {
        callProcessZVA("Config_DisplaySettings.json");
    }
    public void onClickbtn_DesktopUIelems(View v) {
        callProcessZVA("Config_DesktopUIElements.json");
    }
    public void onClickbtn_StatusIcons(View v) {
        callProcessZVA("Config_StatusIcons.json");
    }
    public void onClickbtn_AppsBehaviors(View v) {
        callProcessZVA("Config_AppBehaviors.json");
    }
    public void onClickbtn_Taskbar(View v) {
        callProcessZVA("Config_Taskbar.json");
    }
    public void onClickbtn_DeviceBehaviors(View v) { callProcessZVA("Config_DeviceBehaviors.json");    }

    public void onClickbtn_OperatorOne(View v) {
        callProcessZVA("Config_MANAGER.json");
    }

    public void onClickbtn_OperatorTwo(View v) {
        callProcessZVA("Config_DIMENSIONING.json");
    }

    public void onClickbtn_OperatorThree(View v) {
        sendWSCConfigViaSSM();
    }


    void callProcessZVA(String jsonConfig){
        try {
            if (iServiceBinder != null) {
                //String dataSet = loadJSONFromSDCard();

                String dataSet = loadJSONFromAsset( jsonConfig );
                String response = iServiceBinder.processZVARequest(dataSet);
                Log.i("callProcessZVA", "processZVARequest response=" + response);
                makeText(this, "processZVARequest response=" + response, Toast.LENGTH_SHORT).show();
            } else {
                Log.e("callProcessZVA", "res " + null);
                makeText(this, "Not Connected", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            makeText(this, "ZVA Excp \n"+e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindtoZVAService();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        //iServiceBinder = com.zebra.workstationconnect.IZVAService.Stub.asInterface(service);  //old interface
        iServiceBinder = com.zebra.valueadd.IZVAService.Stub.asInterface(service);
        Log.e("TAG", "WSC connected");
        makeText(this, "WSC Connected", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        makeText(getApplicationContext(), "IPC server has disconnected unexpectedly", Toast.LENGTH_LONG).show();
        iServiceBinder = null;

    }
    String pkg="com.zebra.workstationconnect.release";
    private void bindtoZVAService() {
        Intent intent = new Intent("com.zebra.workstationconnect.release");
        intent.setClassName("com.zebra.workstationconnect.release", "com.zebra.workstationconnect.DeviceManagementService");
        bindService(intent, this, BIND_AUTO_CREATE);
    }

    public String loadJSONFromAsset(String jsonAssetFileName) {
        String json = null;
        try {
            InputStream is = this.getAssets().open(jsonAssetFileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }


    private final String AUTHORITY_FILE = "content://com.zebra.securestoragemanager.securecontentprovider/files/";
    private final String RETRIEVE_AUTHORITY = "content://com.zebra.securestoragemanager.securecontentprovider/file/*";
    private final String COLUMN_DATA_NAME = "data_name";
    private final String COLUMN_DATA_VALUE = "data_value";
    private final String COLUMN_DATA_TYPE = "data_type";
    private final String COLUMN_DATA_PERSIST_REQUIRED = "data_persist_required";
    private final String COLUMN_TARGET_APP_PACKAGE = "target_app_package";
    private final String signature = "";
    String TAG="sendWSCConfigViaSSM";
    public void sendWSCConfigViaSSM(){

        String tmpLocalConfigFile="/sdcard/Download/Config.json";
        String _target_package ="com.zebra.workstationconnect.release";
        String _target_sig="";
        String targetPath = "com.zebra.workstationconnect.release/enterprise/workstation_connect_config.txt";

        //LOAD ASSET
        String jsonAsset = loadJSONFromAsset("Config_MANAGER.json");

        //SAVE TO LOCAL FILE
        saveStringToLocalFile(tmpLocalConfigFile, jsonAsset);

        //FEED THE LOCAL FILE TO SSM
        FeedLocalFileToSSM(tmpLocalConfigFile, _target_package, _target_sig, targetPath);

    }

    private void FeedLocalFileToSSM(String srcFile, String _target_package, String _target_sig, String targetPath) {
        Uri cpUriQuery = Uri.parse(AUTHORITY_FILE + getPackageName());
        Log.i(TAG, "authority  " + cpUriQuery.toString());

        StringBuilder _sb = new StringBuilder();

        try {
            ContentValues values = new ContentValues();
            String _package_sig = "{\"pkg\":\""+ _target_package +"\",\"sig\":\"" + _target_sig + "\"}";
            String allPackagesSigs = "{\"pkgs_sigs\":["+ _package_sig  + "]}" ;

            values.put(COLUMN_TARGET_APP_PACKAGE, allPackagesSigs);
            values.put(COLUMN_DATA_NAME, srcFile);
            values.put(COLUMN_DATA_TYPE, "3");
            values.put(COLUMN_DATA_VALUE, targetPath);
            values.put(COLUMN_DATA_PERSIST_REQUIRED, "false");
            Uri createdRow = getContentResolver().insert(cpUriQuery, values);
            Log.i(TAG, "SSM Insert File: " + createdRow.toString());
            //Toast.makeText(this, "File insert success", Toast.LENGTH_SHORT).show();
            _sb.append("Insert Result rows: "+createdRow+"\n" );
        } catch (Exception e) {
            Log.e(TAG, "SSM Insert File - error: " + e.getMessage() + "\n\n");
            _sb.append("SSM Insert File - error: " + e.getMessage() + "\n\n");
        }
    }

    private void saveStringToLocalFile(String srcFile, String jsonAsset) {
        try {
            File f = new File(srcFile);
            if (f.exists()) {
                f.delete();
            }

            f.createNewFile();
            Process _p = Runtime.getRuntime().exec("chmod 666 " + srcFile); //chmod needed for /enterprise
            _p.waitFor();
            Log.i(TAG, "chmod 666 result="+_p.exitValue());

            FileOutputStream fos = new FileOutputStream(f);
            fos.write( jsonAsset.getBytes(StandardCharsets.UTF_8) );
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}