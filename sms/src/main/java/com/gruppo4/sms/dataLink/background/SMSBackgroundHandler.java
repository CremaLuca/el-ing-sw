package com.gruppo4.sms.dataLink.background;

import android.content.Context;
import android.content.Intent;

/**
 * Keeps the broadcast receiver alive while the app is closed
 *
 * @author Luca Crema
 */
public class SMSBackgroundHandler {

    public static void onAppDestroy(Context context) {
        Intent service = new Intent(context, SMSKeepAliveService.class);
        context.startService(service);
    }

    public static void onAppCreate(Context context) {
        Intent service = new Intent(context, SMSKeepAliveService.class);
        context.stopService(service);
    }


}
