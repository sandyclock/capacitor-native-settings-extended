package nl.raphael.settings;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS;
import static android.provider.Settings.ACTION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static nl.raphael.settings.AndroidSettings.ConnectedDeviceDashboardActivity;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NativeSettings")
public class NativeSettingsPlugin extends Plugin {

  public static final String ConnectedDeviceDashboardActivity = "ConnectedDeviceDashboardActivity";

    @PluginMethod
    public void open(PluginCall call) {
        String option = call.getString("optionAndroid");
        String setting = AndroidSettings.getAction(option);

        // Check if settings is available.
        if (setting == null) {
            call.reject("Could not find native android setting: " + option);
            return;
        }

        this.openOption(call, setting);
    }

    @PluginMethod
    public void openAndroid(PluginCall call) {
        String option = call.getString("option");
        String setting = AndroidSettings.getAction(option);

        // Check if settings is available.
        if (setting == null) {
            call.reject("Could not find native android setting: " + option);
            return;
        }

        this.openOption(call, setting);
    }

    /**
     * Reports whether the device has debug-oriented options enabled. Stripe
     * Terminal v5 refuses production Tap to Pay reader discovery (with a
     * TAP_TO_PAY_INSECURE_ENVIRONMENT error) when Developer Options or USB/Wi-Fi
     * debugging is on, so the app reads this up front to warn the user instead
     * of letting discovery silently time out.
     */
    @PluginMethod
    public void getDebugState(PluginCall call) {
        boolean developerOptions = isGlobalSettingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        boolean adb = isGlobalSettingEnabled(Settings.Global.ADB_ENABLED);
        boolean appDebuggable = isAppDebuggable();

        JSObject ret = new JSObject();
        ret.put("developerOptionsEnabled", developerOptions);
        ret.put("adbEnabled", adb);
        ret.put("appDebuggable", appDebuggable);
        ret.put("anyDebugEnabled", developerOptions || adb);
        call.resolve(ret);
    }

    /**
     * True when the running app is a debuggable build (android:debuggable="true"
     * in the merged manifest, i.e. FLAG_DEBUGGABLE). Stripe Terminal v5 refuses
     * production Tap to Pay from a debuggable app regardless of device settings.
     */
    private boolean isAppDebuggable() {
        try {
            ApplicationInfo info = getContext().getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGlobalSettingEnabled(String name) {
        try {
            return Settings.Global.getInt(getContext().getContentResolver(), name, 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void openOption(PluginCall call, String setting) {
        Intent intent = new Intent();

        // Application details requires package name as URI.
        if (ACTION_APPLICATION_DETAILS_SETTINGS.equals(setting)) {
            intent.setAction(setting);
            intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
        } else if (ACTION_APP_NOTIFICATION_SETTINGS.equals(setting)) { // App notification settings requires package name as extra app package.
            intent.setAction(setting);
            intent.putExtra(EXTRA_APP_PACKAGE, getActivity().getPackageName());
        } else if (ConnectedDeviceDashboardActivity.equals(setting)){
//          https://stackoverflow.com/questions/66010835/intent-for-settings-connected-devices-connection-preferences-page
 //         Logger.info("***************** open native setting for connected devices:");
//          ConnectedDeviceDashboardActivity
//          intent.setClassName("com.android.settings", "com.android.settings.Settings$" + "AdvancedConnectedDeviceActivity");
          intent.setClassName("com.android.settings", "com.android.settings.Settings$" + ConnectedDeviceDashboardActivity);

        }
        else {
            intent.setAction(setting);
        }

        startActivityForResult(call, intent, "activityResult");
    }

    /**
     * Send response on activityResult (when intent closes)
     */
    @ActivityCallback
    private void activityResult(PluginCall call, ActivityResult result) {
        JSObject js = new JSObject();
        js.put("status", true);
        call.resolve(js);
    }
}
