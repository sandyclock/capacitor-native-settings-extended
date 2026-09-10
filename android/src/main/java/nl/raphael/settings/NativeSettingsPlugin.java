package nl.raphael.settings;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS;
import static android.provider.Settings.ACTION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static nl.raphael.settings.AndroidSettings.ConnectedDeviceDashboardActivity;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
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
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "NativeSettings",
    permissions = {
        @Permission(alias = NativeSettingsPlugin.MICROPHONE_ALIAS, strings = { Manifest.permission.RECORD_AUDIO })
    }
)
public class NativeSettingsPlugin extends Plugin {

  public static final String ConnectedDeviceDashboardActivity = "ConnectedDeviceDashboardActivity";

  /*
   * Logger.warn has no (tag, message, Throwable) overload -- only Logger.error
   * does -- so the paths below fold the exception into the message. That is the
   * right shape anyway: a missing setting, and a settings screen that will not
   * open, are expected outcomes here rather than errors, and a stack trace for
   * either would be noise.
   */
  private static final String LOG_TAG = "NativeSettings";

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
     * Reads one setting by name and reports its raw value.
     *
     * The answer is deliberately three-state -- a value, or absent -- because
     * for a vendor feature toggle "switched off" and "this build has no such
     * feature" call for completely different behaviour from the caller, and a
     * boolean return would merge them.
     *
     * Reading needs no permission. Writing would need WRITE_SETTINGS and is not
     * offered here on purpose: these keys belong to the user.
     */
    @PluginMethod
    public void getDeviceSetting(PluginCall call) {
        String key = call.getString("key");
        if (key == null || key.trim().isEmpty()) {
            call.reject("getDeviceSetting requires a key");
            return;
        }

        String scope = call.getString("scope", "system");
        if (scope == null) {
            scope = "system";
        }
        scope = scope.trim().toLowerCase();
        // Keep in sync with DEVICE_SETTING_SCOPES in src/definitions.ts, which is where the
        // TypeScript type and the web guard both derive from. A bridge cannot import a TS
        // constant, so these three strings are a deliberate second copy -- the list is closed.
        //
        // Reject rather than fall back to "system". A typo ("sytem") would otherwise read a
        // DIFFERENT table and return a perfectly well-formed present/absent answer for it -- the
        // same class of invisible wrong result as an intent that resolves but lands elsewhere.
        // Since the reply echoes `scope`, a silent correction also makes the echo misleading.
        // Done before the first publish of this method, while there is nothing to break.
        if (!"system".equals(scope) && !"secure".equals(scope) && !"global".equals(scope)) {
            call.reject("getDeviceSetting scope must be one of: system, secure, global");
            return;
        }

        String value = null;
        try {
            ContentResolver resolver = getContext().getContentResolver();
            if ("secure".equals(scope)) {
                value = Settings.Secure.getString(resolver, key);
            } else if ("global".equals(scope)) {
                value = Settings.Global.getString(resolver, key);
            } else {
                value = Settings.System.getString(resolver, key);
            }
        } catch (Exception e) {
            // An unreadable setting is reported as absent, not as a failure: the
            // caller's next move is the same either way, and a rejection would
            // push every call site into a try/catch for a normal outcome.
            Logger.warn(LOG_TAG, "could not read setting " + key + " (" + scope + "): " + e);
            value = null;
        }

        JSObject ret = new JSObject();
        ret.put("key", key);
        ret.put("scope", scope);
        /*
         * 🔴 JSONObject.put(name, (Object) null) REMOVES the mapping, so the key
         * would arrive in JavaScript as `undefined` rather than `null` and a
         * caller testing `value === null` would silently never match. The NULL
         * sentinel is what crosses the bridge as a real null.
         */
        ret.put("value", value == null ? JSONObject.NULL : value);
        ret.put("present", value != null);
        call.resolve(ret);
    }

    /**
     * Reports whether any supplied target resolves, without opening anything.
     *
     * A false answer is weaker than it looks: since Android 11, package
     * visibility can hide an activity that exists, so this can be a false
     * negative unless the app declares the intents in a <queries> element.
     * openVendorSetting() therefore does not trust this and attempts the launch
     * for real.
     */
    @PluginMethod
    public void canOpenVendorSetting(PluginCall call) {
        JSArray candidates = call.getArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            call.reject("canOpenVendorSetting requires a non-empty candidates array");
            return;
        }

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject target = candidates.optJSONObject(i);
            Intent intent = intentForTarget(target);
            if (intent == null) {
                continue;
            }
            try {
                if (getContext().getPackageManager().resolveActivity(intent, 0) != null) {
                    JSObject ret = new JSObject();
                    ret.put("available", true);
                    ret.put("matched", target);
                    call.resolve(ret);
                    return;
                }
            } catch (Exception e) {
                Logger.warn(LOG_TAG, "could not resolve a vendor settings target: " + e);
            }
        }

        JSObject ret = new JSObject();
        ret.put("available", false);
        ret.put("matched", JSONObject.NULL);
        call.resolve(ret);
    }

    /**
     * Opens the first supplied target the device will accept.
     *
     * Each candidate is launched for real rather than pre-filtered through
     * resolveActivity(), so a target hidden from the resolver by package
     * visibility still opens.
     *
     * Both failure modes are caught, and the second is the one that surprises
     * people: ActivityNotFoundException when nothing handles the intent, and
     * SecurityException when the activity exists but is not exported -- common
     * for vendor settings screens, and NOT something resolveActivity() warns
     * about beforehand.
     */
    @PluginMethod
    public void openVendorSetting(PluginCall call) {
        JSArray candidates = call.getArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            call.reject("openVendorSetting requires a non-empty candidates array");
            return;
        }

        for (int i = 0; i < candidates.length(); i++) {
            JSONObject target = candidates.optJSONObject(i);
            Intent intent = intentForTarget(target);
            if (intent == null) {
                continue;
            }
            try {
                /*
                 * No FLAG_ACTIVITY_NEW_TASK, deliberately. Started from the
                 * Activity's context the settings screen joins THIS app's task,
                 * which is what lets it survive a kiosk that re-fronts itself on
                 * pause: the task being pulled forward is already frontmost, so
                 * the pull is a no-op and the settings screen stays on top.
                 * Verified on a contained device 2026-09-08. Adding NEW_TASK
                 * would hand it its own task and reintroduce the yank.
                 */
                getActivity().startActivity(intent);
                JSObject ret = new JSObject();
                ret.put("opened", true);
                ret.put("matched", target);
                call.resolve(ret);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Logger.warn(LOG_TAG, "a vendor settings target would not open; trying the next: " + e);
            } catch (Exception e) {
                Logger.warn(LOG_TAG, "unexpected failure opening a vendor settings target: " + e);
            }
        }

        JSObject ret = new JSObject();
        ret.put("opened", false);
        ret.put("matched", JSONObject.NULL);
        call.resolve(ret);
    }

    /**
     * Builds an intent from one candidate, or null when the candidate names
     * nothing usable.
     *
     * An entry with neither an action nor a complete component is skipped
     * rather than launched: an empty Intent resolves to something arbitrary,
     * which is a worse outcome than doing nothing.
     */
    private Intent intentForTarget(JSONObject target) {
        if (target == null) {
            return null;
        }

        String action = optTrimmed(target, "action");
        String pkg = optTrimmed(target, "package");
        String activity = optTrimmed(target, "activity");

        Intent intent = new Intent();
        boolean addressed = false;

        if (action != null) {
            intent.setAction(action);
            addressed = true;
        }
        if (pkg != null && activity != null) {
            intent.setClassName(pkg, activity);
            addressed = true;
        }

        return addressed ? intent : null;
    }

    /**
     * A string field, or null when it is missing, JSON null, or blank.
     *
     * optString() alone will not do: it returns "" for a missing field and the
     * literal "null" for a JSON null, both of which would be treated as real
     * values here.
     */
    private String optTrimmed(JSONObject source, String name) {
        if (source == null || !source.has(name) || source.isNull(name)) {
            return null;
        }
        String value = source.optString(name, null);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
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
