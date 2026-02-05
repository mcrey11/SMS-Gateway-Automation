package com.gateway.sms;

public class GatewayApplication extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            timber.log.Timber.plant(new timber.log.Timber.DebugTree());
        }
    }
}
