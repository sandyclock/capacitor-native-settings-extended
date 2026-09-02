package nl.raphael.settings;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS;
import static android.provider.Settings.ACTION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static nl.raphael.settings.AndroidSettings.ConnectedDeviceDashboardActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "NativeSettings",
    permissions = {
        @Permission(alias = NativeSettingsPlugin.MICROPHONE_ALIAS, strings = { Manifest.permission.RECORD_AUDIO })
    }
)
public class NativeSettingsPlugin extends Plugin {

  public static final String ConnectedDeviceDashboardActivity = "ConnectedDeviceDashboardActivity";

    /**
     * Alias for RECORD_AUDIO. The annotation only registers the alias for the
     * runtime request; it does NOT put the permission in the merged manifest.
     * That is deliberate: this plugin is installed in apps that never capture
     * audio, and declaring a dangerous permission they do not use would be a
     * store-listing problem for them. Each app declares RECORD_AUDIO itself,
     * and {@link #microphoneState()} reports 'unsupported' where it has not.
     */
    /*
     * ⚠️ Registering an alias also gives this plugin Capacitor's inherited
     * `checkPermissions()` / `requestPermissions()` with a `microphone` key,
     * where before it had none and they resolved empty. Those generic methods do
     * NOT go through isMicrophoneDeclared(), so calling them on a host that does
     * not declare RECORD_AUDIO would ask the OS directly. Use the dedicated
     * checkMicrophonePermission() / requestMicrophonePermission() instead.
     */
    public static final String MICROPHONE_ALIAS = "microphone";

    private static final String PREFS_NAME = "capacitor-native-settings-extended";

    /**
     * Records that we have put the RECORD_AUDIO dialog on screen at least once.
     *
     * Android gives no API for "has this permission ever been requested", and
     * without that the two states we most need to tell apart are identical:
     * never-asked and permanently-refused BOTH report not-granted with
     * shouldShowRequestPermissionRationale() == false. This flag supplies the
     * missing bit.
     */
    private static final String KEY_MICROPHONE_REQUESTED = "microphoneRequested";

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
     * Reports the microphone permission state without ever showing a dialog.
     */
    @PluginMethod
    public void checkMicrophonePermission(PluginCall call) {
        call.resolve(microphoneState());
    }

    /**
     * Requests the microphone permission and resolves with the resulting state.
     *
     * This deliberately goes to the OS even when microphoneState() would report
     * blocked. Android resets permissions for unused apps, so a blocked reading
     * can be stale; if it is not stale the OS returns denied immediately with no
     * dialog, which costs the user nothing. Believing our own cached verdict
     * would leave a recoverable device permanently mute.
     */
    @PluginMethod
    public void requestMicrophonePermission(PluginCall call) {
        if (isMicrophoneGranted() || !isMicrophoneDeclared()) {
            call.resolve(microphoneState());
            return;
        }

        requestPermissionForAlias(MICROPHONE_ALIAS, call, "microphonePermissionCallback");
    }

    @PermissionCallback
    private void microphonePermissionCallback(PluginCall call) {
        /*
         * Mark HERE, not before the request. Marking first meant a process death
         * between the write and the dialog left a flag saying we had asked when
         * the user had never seen anything -- and that reads as `blocked`, which
         * is the expensive state (operator badge, and diners told to fetch staff).
         *
         * This still self-corrects after an app-data wipe: the wipe clears the
         * flag, the next request goes to the OS, and the OS invokes this callback
         * even when it shows no dialog -- measured, a blocked request resolves
         * through here in ~140 ms -- so the flag is restored either way.
         */
        markMicrophoneRequested();
        call.resolve(microphoneState());
    }

    /**
     * Collapses the Android permission APIs into the three facts a caller needs:
     * what the state is, whether asking again can still raise a dialog, and
     * whether only the settings screen is left.
     */
    private JSObject microphoneState() {
        String status;
        boolean canRequest;
        boolean blocked;

        if (!isMicrophoneDeclared()) {
            // No RECORD_AUDIO in the merged manifest. Requesting would be denied
            // instantly and forever, and sending the user to Settings would show
            // them a screen with no microphone row on it -- so say so plainly
            // rather than reporting a denial the user could act on.
            status = "unsupported";
            canRequest = false;
            blocked = false;
        } else if (isMicrophoneGranted()) {
            status = "granted";
            canRequest = false;
            blocked = false;
        } else if (shouldShowMicrophoneRationale()) {
            // Refused at least once, but Android will still show the dialog.
            status = "denied";
            canRequest = true;
            blocked = false;
        } else if (!hasRequestedMicrophone()) {
            // Never asked on this install -- the first request shows the dialog.
            status = "prompt";
            canRequest = true;
            blocked = false;
        } else {
            // Asked before, no rationale owed, still not granted: "Don't allow"
            // was chosen twice, or a policy blocks it. Only Settings can fix it.
            status = "denied";
            canRequest = false;
            blocked = true;
        }

        JSObject ret = new JSObject();
        ret.put("status", status);
        ret.put("canRequest", canRequest);
        ret.put("blocked", blocked);
        return ret;
    }

    private boolean isMicrophoneGranted() {
        try {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True when RECORD_AUDIO appears in the app's merged manifest. Without the
     * declaration every request is denied instantly, which is indistinguishable
     * from a user refusal unless we check for it here.
     */
    private boolean isMicrophoneDeclared() {
        try {
            Context context = getContext();
            PackageInfo info = context
                .getPackageManager()
                .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions == null) {
                return false;
            }
            for (String permission : info.requestedPermissions) {
                if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Logger.error("NativeSettings", "Could not read the manifest permissions", e);
            // Assume it is declared: a false 'unsupported' would hide a working
            // enable button, which is the worse of the two mistakes.
            return true;
        }
    }

    private boolean shouldShowMicrophoneRationale() {
        try {
            Activity activity = getActivity();
            return activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasRequestedMicrophone() {
        return prefs().getBoolean(KEY_MICROPHONE_REQUESTED, false);
    }

    private void markMicrophoneRequested() {
        prefs().edit().putBoolean(KEY_MICROPHONE_REQUESTED, true).apply();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
