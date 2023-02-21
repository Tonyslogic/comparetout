package com.tfcode.comparetout.model;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class ServedService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1,new Notification());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
