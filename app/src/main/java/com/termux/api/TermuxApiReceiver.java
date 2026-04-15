package com.termux.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.termux.api.apis.BatteryStatusAPI;
import com.termux.api.apis.CameraInfoAPI;
import com.termux.api.apis.ClipboardAPI;
import com.termux.api.apis.SensorAPI;
import com.termux.api.apis.WifiAPI;
import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.plugins.TermuxPluginUtils;

public class TermuxApiReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxApiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));

        try {
            doWork(context, intent);
        } catch (Throwable t) {
            String message = "Error in " + LOG_TAG;
            // Make sure never to throw exception from BroadCastReceiver to avoid "process is bad"
            // behaviour from the Android system.
            Logger.logStackTraceWithMessage(LOG_TAG, message, t);

            TermuxPluginUtils.sendPluginCommandErrorNotification(context, LOG_TAG,
                    TermuxConstants.TERMUX_API_APP_NAME + " Error", message, t);

            ResultReturner.noteDone(this, intent);
        }
    }

    private void doWork(Context context, Intent intent) {
        String apiMethod = intent.getStringExtra("api_method");
        if (apiMethod == null) {
            Logger.logError(LOG_TAG, "Missing 'api_method' extra");
            return;
        }

        switch (apiMethod) {
            case "BatteryStatus":
                BatteryStatusAPI.onReceive(this, context, intent);
                break;
            case "CameraInfo":
                CameraInfoAPI.onReceive(this, context, intent);
                break;
            case "Clipboard":
                ClipboardAPI.onReceive(this, context, intent);
                break;
            case "Sensor":
                SensorAPI.onReceive(context, intent);
                break;
            case "WifiConnectionInfo":
                WifiAPI.onReceiveWifiConnectionInfo(this, context, intent);
                break;
            default:
                Logger.logError(LOG_TAG, "Unrecognized 'api_method' extra: '" + apiMethod + "'");
        }
    }

}
