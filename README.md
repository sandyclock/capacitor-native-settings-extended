# Capacitor Native Settings

Capacitor plugin to open native settings screens for Android and iOS.

## Plugin versions

| Capacitor version | Plugin version                                    |
| ---------- | ----------------------------------------- |
| v7 | >= v7.0.1 |
| v6 | >= v6.0.0 |
| v5 | >= v5.0.0 |
| v4 | >= v4.0.0 |
| v3 | <= v2.0.1 |

## Install

```bash
npm install capacitor-native-settings-extended
npx cap sync
```

## Example

```JS
import { NativeSettings, AndroidSettings, IOSSettings } from 'capacitor-native-settings-extended';

/**
 * Note that the only supported option by Apple is "App".
 * Using other options might break in future iOS versions
 * or have your app rejected from the App Store.
 */
NativeSettings.open({
  optionAndroid: AndroidSettings.ApplicationDetails, 
  optionIOS: IOSSettings.App
})

NativeSettings.openAndroid({
  option: AndroidSettings.ApplicationDetails,
});

/**
 * Note that the only supported option by Apple is "App".
 * Using other options might break in future iOS versions
 * or have your app rejected from the App Store.
 */
NativeSettings.openIOS({
  option: IOSSettings.App,
});
```

## Reading a vendor setting, and offering its screen

Some device features are exposed only as an undocumented row in a vendor's own
settings table — no public API, no constant, and a name that differs between
OEMs. `getDeviceSetting()` reads one by name, and the answer is **three-state**:

```JS
import { NativeSettings } from 'capacitor-native-settings-extended';

const wake = await NativeSettings.getDeviceSetting({
  key: 'double_tab_to_wake_up',   // vendor-specific; spelling varies by OEM
  scope: 'system',                // 'system' (default) | 'secure' | 'global'
});

if (!wake.present) {
  // The key does not exist: this build has no such feature. No settings screen
  // will ever create it, so do not offer one -- an un-actionable prompt trains
  // people to ignore every prompt.
} else if (wake.value === '1') {
  // Present and on.
} else {
  // Present and OFF -- the actionable case, and the one a boolean API loses.
  // This is where offering the settings screen is worth doing.
}
```

Reading needs **no permission**. Writing is deliberately not offered: these keys
belong to the user, and an app changing them behind their back is the behaviour
this plugin exists to avoid.

Where the feature exists but is off, you can try to send the user straight to
it. Vendor screens are addressed by intent action or by explicit component, and
neither is stable across builds — so the **caller** supplies the candidates and
the plugin just tries them in order:

```JS
const candidates = [
  // Most specific FIRST -- see the ordering caution below.
  { package: 'com.android.settings', activity: 'com.android.settings.Settings$SomeMotionActivity' },
  { action: 'com.example.vendor.MOTION_SETTINGS' },
];

// Decide whether to render the button at all.
const probe = await NativeSettings.canOpenVendorSetting({ candidates });

// ...and let the tap be the final word.
if (probe.available) {
  const opened = await NativeSettings.openVendorSetting({ candidates });
  if (!opened.opened) {
    // Nothing accepted it after all -- fall back to the general settings screen
    // and written instructions.
  }
}
```

🔴 **Order the candidates most-specific first, and here is why.** An intent
that *resolves* is not an intent that *lands where you meant*. A broad vendor
**action** can be claimed by the Settings app as a whole and drop the user on the
Settings **home page**: it resolves, it starts, it reports success -- and it goes
somewhere else. An explicit **component** for the same screen lands exactly
right. Since the first candidate that starts wins, an action listed ahead of a
component means the component is never tried.

🔴 **`opened: true` means the launch did not throw. It does not mean the
correct screen is showing.** Nothing available to this plugin can tell the
difference: `startActivity()` reports that *something* handled the intent, and
`startActivityForResult()` does not name the activity that was landed on. Task
and usage APIs are delayed, permission-gated, or both. Only a person looking at
the screen can confirm the landing -- so treat `opened` as *"we handed off"*, and
pair the hand-off with written instructions for the case where it goes astray.
For the same reason `available: true` means only *"something claims this
intent"*, never *"the screen you want exists"*.

⚠️ **`canOpenVendorSetting()` can be a false negative.** Since Android 11,
package visibility can hide an activity that genuinely exists, so a `false` may
mean "not visible to this app" rather than "not there". **If you render a button
off this probe, you must declare the candidates in a `<queries>` element of your
manifest** -- otherwise the probe is not merely imprecise, it is unreliable in
the one direction that matters: it hides a button for a screen that is really
there. Declare an `<intent>` child per action and a `<package>` entry per
explicit component:

```xml
<queries>
    <intent>
        <action android:name="com.example.vendor.MOTION_SETTINGS" />
    </intent>
    <package android:name="com.android.settings" />
</queries>
```

`openVendorSetting()` does **not** trust the probe: it launches each candidate
for real and catches the refusal, so a hidden-but-present screen still opens.
Use the probe to decide whether to *show* a button; use the open to find out.

⚠️ Two failures are caught, and the second surprises people:
`ActivityNotFoundException` when nothing handles the intent, and
`SecurityException` when the activity exists but is **not exported** — common for
vendor settings screens, and something `resolveActivity()` gives no warning about.

### `KnownVendorSettingTargets` — observed screens, as data

Rediscovering a vendor's strings — and rediscovering the ordering trap above the
hard way — is worse than shipping the few that are known, so they are exported
as an ordinary value you can pass, spread, edit, or ignore:

```JS
import { NativeSettings, KnownVendorSettingTargets } from 'capacitor-native-settings-extended';

await NativeSettings.openVendorSetting({
  candidates: KnownVendorSettingTargets.motionAndGestureSettings,
});
```

The names are typed off the constant itself (`KnownVendorSettingName =
keyof typeof KnownVendorSettingTargets`), so the list and its type cannot drift,
and each entry is checked against `VendorSettingTarget` where it is written —
a component missing its activity fails to compile rather than being skipped on a
device.

⚠️ **This is data, not a stable API.** Every entry was seen working on **one**
device, on **one** firmware build, on the date in its doc comment. OEM screens
move between builds, so treat a miss as expected and pass your own candidates
when you know better. A changed string here is a **minor** version bump, never a
patch — pin accordingly if you depend on a specific value.

## API

<docgen-index>

* [`open(...)`](#open)
* [`openAndroid(...)`](#openandroid)
* [`openIOS(...)`](#openios)
* [`getDeviceSetting(...)`](#getdevicesetting)
* [`canOpenVendorSetting(...)`](#canopenvendorsetting)
* [`openVendorSetting(...)`](#openvendorsetting)
* [`getDebugState()`](#getdebugstate)
* [`checkMicrophonePermission()`](#checkmicrophonepermission)
* [`requestMicrophonePermission()`](#requestmicrophonepermission)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### open(...)

```typescript
open(option: PlatformOptions) => Promise<{ status: boolean; }>
```

Opens the specified options on android & ios.
Note that the only supported option by Apple is "App". Using other options
might break in future iOS versions or have your app rejected in the App Store.

| Param        | Type                                                        | Description                                    |
| ------------ | ----------------------------------------------------------- | ---------------------------------------------- |
| **`option`** | <code><a href="#platformoptions">PlatformOptions</a></code> | <a href="#platformoptions">PlatformOptions</a> |

**Returns:** <code>Promise&lt;{ status: boolean; }&gt;</code>

--------------------


### openAndroid(...)

```typescript
openAndroid(option: AndroidOptions) => Promise<{ status: boolean; }>
```

Opens the specified option in android.
Only use this if you have made sure the user is on android.
This can be done by checking the platform before hand.

| Param        | Type                                                      | Description                                  |
| ------------ | --------------------------------------------------------- | -------------------------------------------- |
| **`option`** | <code><a href="#androidoptions">AndroidOptions</a></code> | <a href="#androidoptions">AndroidOptions</a> |

**Returns:** <code>Promise&lt;{ status: boolean; }&gt;</code>

--------------------


### openIOS(...)

```typescript
openIOS(option: IOSOptions) => Promise<{ status: boolean; }>
```

Opens the specified option on iOS.
Only use this if you have made sure the user is on iOS.
This can be done by checking the platform before hand.

Note that the only supported option by Apple is "App". Using other options
might break in future iOS versions or have your app rejected in the App Store.

| Param        | Type                                              | Description                          |
| ------------ | ------------------------------------------------- | ------------------------------------ |
| **`option`** | <code><a href="#iosoptions">IOSOptions</a></code> | <a href="#iosoptions">IOSOptions</a> |

**Returns:** <code>Promise&lt;{ status: boolean; }&gt;</code>

--------------------


### getDeviceSetting(...)

```typescript
getDeviceSetting(options: DeviceSettingOptions) => Promise<DeviceSettingResult>
```

Reads ONE device setting by name and reports its raw value. Android only —
on iOS and web it resolves `present: false, value: null`.

🔑 The value is returned as a RAW STRING, deliberately, because the useful
answer here is three-state and a boolean would destroy it:

- `"1"`  — the setting exists and is on
- `"0"`  — the setting exists and is OFF (the user can turn it on)
- `null` — the key is not present: this build has no such feature at all,
           and no trip to any settings screen will ever create it

The middle state is the actionable one and the one a boolean API loses. A
caller that only wants "is it on" can compare to `"1"`; a caller deciding
whether to OFFER a settings shortcut needs to tell `"0"` from `null`.

Reading needs NO permission and does no I/O worth caching around. Writing
would need `WRITE_SETTINGS` and is deliberately NOT offered: these keys are
the user's, and a kiosk silently changing them is the behaviour this plugin
exists to avoid.

⚠️ Vendor keys are not API. Names differ between OEMs, appear and disappear
between builds of the same brand, and are not documented anywhere. `null`
is therefore an ordinary answer, not an error — see
{@link NativeSettingsPlugin.canOpenVendorSetting} for the matching caution
about opening one.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#devicesettingoptions">DeviceSettingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#devicesettingresult">DeviceSettingResult</a>&gt;</code>

--------------------


### canOpenVendorSetting(...)

```typescript
canOpenVendorSetting(options: VendorSettingOptions) => Promise<VendorSettingProbeResult>
```

Reports whether any of the supplied targets can be opened on this device,
WITHOUT opening anything. Android only.

Candidates are tried in order and the first that resolves is reported, so
a caller can pass its best guess first and fall back to a broader screen.

🔴 A `false` here is weaker evidence than it looks, and callers must treat
it as "probably not" rather than "definitely not". Since Android 11 package
visibility can hide an activity that genuinely exists, this probe can be a
FALSE NEGATIVE unless the app declares the relevant intents in a `&lt;queries&gt;`
element of its manifest. {@link NativeSettingsPlugin.openVendorSetting}
therefore attempts each candidate for real rather than trusting this — use
this method to decide whether to show a button, and let the open attempt be
the final word.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#vendorsettingoptions">VendorSettingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#vendorsettingproberesult">VendorSettingProbeResult</a>&gt;</code>

--------------------


### openVendorSetting(...)

```typescript
openVendorSetting(options: VendorSettingOptions) => Promise<VendorSettingOpenResult>
```

Opens the first of the supplied targets that this device will accept.
Android only.

🔑 This does NOT pre-filter on
{@link NativeSettingsPlugin.canOpenVendorSetting}. Each candidate is
launched for real and a refusal is caught, so a target hidden from the
resolver by package visibility still opens. The trade is that a genuinely
absent target costs one caught exception per candidate, which is cheap and
happens once, in response to a tap.

⚠️ **This method holds no vendor knowledge.** It launches whatever the
caller supplies, exactly as {@link NativeSettingsPlugin.getDeviceSetting}
reads whatever key the caller names — OEM screens move between builds, and
baking component names into the native path would mean republishing the
plugin every time one moved.

The package does ship {@link KnownVendorSettingTargets}: an optional,
separately exported list of screens that have been observed to open. It is
data a caller may pass, not behaviour this method applies, and it is
explicitly not a stable API — see its own caution. Nothing here consults
it, and a caller that ignores it loses nothing.

🔑 A candidate carrying **both** an action and a component is ONE intent,
not a fallback pair: the action is set, then the explicit component, and
the component wins resolution. Trying one form and then another is what the
`candidates` LIST does — order it most-specific first.

🔑 **This survives an armed kiosk, and the reason is the TASK.** The
activity is started from the host Activity's context with no
`FLAG_ACTIVITY_NEW_TASK`, so the settings screen joins the app's OWN task
rather than starting its own. A kiosk that re-front's itself on pause is
then a no-op: the task it pulls forward is already the frontmost one, and
the settings screen is on top of it. Verified 2026-09-08 on a contained
device — `isInKioskMode` true, the screen opened and stayed for the whole
observation rather than being pulled back.

🔴 **Do not add `FLAG_ACTIVITY_NEW_TASK` here.** It looks like tidying and
would give the settings screen its own task, which is exactly the shape a
containment re-front can pull back under. If a caller genuinely needs a
separate task, it should drop containment first, the way an app-permission
trip does.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#vendorsettingoptions">VendorSettingOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#vendorsettingopenresult">VendorSettingOpenResult</a>&gt;</code>

--------------------


### getDebugState()

```typescript
getDebugState() => Promise<DeviceDebugState>
```

**Returns:** <code>Promise&lt;<a href="#devicedebugstate">DeviceDebugState</a>&gt;</code>

--------------------


### checkMicrophonePermission()

```typescript
checkMicrophonePermission() => Promise<MicrophonePermissionState>
```

Reports the current state of the microphone (RECORD_AUDIO) permission
WITHOUT ever showing a dialog. Safe to call on every screen.

The point of this method is the distinction the platform APIs do not give
you directly: "never asked" versus "asked and permanently refused". A
customer-facing kiosk needs it, because a single diner who taps Deny turns
the voice agent off for every diner after them, and only a trip to the OS
settings screen can undo it.

**Returns:** <code>Promise&lt;<a href="#microphonepermissionstate">MicrophonePermissionState</a>&gt;</code>

--------------------


### requestMicrophonePermission()

```typescript
requestMicrophonePermission() => Promise<MicrophonePermissionState>
```

Requests the microphone permission, showing the OS dialog when the OS is
still willing to show it, and resolves with the state that resulted.

This always goes to the OS, even when a previous
{@link NativeSettingsPlugin.checkMicrophonePermission} reported
`blocked: true`. Android silently resets permissions for unused apps, so a
cached "blocked" belief can be stale; a genuinely blocked permission simply
resolves denied with no dialog, which costs nothing.

🔴 iOS: the app's `Info.plist` MUST carry `NSMicrophoneUsageDescription`.
Requesting without it terminates the app — that is an iOS rule, not a
plugin behaviour.

To send the user to the screen where a blocked permission can be restored,
call {@link NativeSettingsPlugin.open} with
`{ optionAndroid: <a href="#androidsettings">AndroidSettings.ApplicationDetails</a>, optionIOS: <a href="#iossettings">IOSSettings.App</a> }`.
There is deliberately no separate method for it — that is the same screen.

**Returns:** <code>Promise&lt;<a href="#microphonepermissionstate">MicrophonePermissionState</a>&gt;</code>

--------------------


### Interfaces


#### PlatformOptions

| Prop                | Type                                                        |
| ------------------- | ----------------------------------------------------------- |
| **`optionAndroid`** | <code><a href="#androidsettings">AndroidSettings</a></code> |
| **`optionIOS`**     | <code><a href="#iossettings">IOSSettings</a></code>         |


#### AndroidOptions

| Prop         | Type                                                        |
| ------------ | ----------------------------------------------------------- |
| **`option`** | <code><a href="#androidsettings">AndroidSettings</a></code> |


#### IOSOptions

| Prop         | Type                                                |
| ------------ | --------------------------------------------------- |
| **`option`** | <code><a href="#iossettings">IOSSettings</a></code> |


#### DeviceSettingResult

| Prop          | Type                                                              | Description                                                                                                                                                                         |
| ------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`key`**     | <code>string</code>                                               | Echoed back, so a caller batching reads can tell answers apart.                                                                                                                     |
| **`scope`**   | <code><a href="#devicesettingscope">DeviceSettingScope</a></code> | Echoed back — the scope actually read, with the default applied.                                                                                                                    |
| **`value`**   | <code>string \| null</code>                                       | The raw stored value, or `null` when the key is not present. See {@link NativeSettingsPlugin.getDeviceSetting} for why this is a string.                                            |
| **`present`** | <code>boolean</code>                                              | `false` means the key does not exist on this build — the feature is absent, not merely switched off. Equivalent to `value === null`, named so the distinction is hard to skim past. |


#### DeviceSettingOptions

| Prop        | Type                                                              | Description                                                                                                                                                                                                                                                              |
| ----------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`key`**   | <code>string</code>                                               | The setting name, exactly as the vendor spells it. Case-sensitive, and not validated: an unknown name is answered `present: false`, which is indistinguishable from a device that lacks the feature. Prefer a constant in your own code over a literal at the call site. |
| **`scope`** | <code><a href="#devicesettingscope">DeviceSettingScope</a></code> | Defaults to `system`.                                                                                                                                                                                                                                                    |


#### VendorSettingProbeResult

| Prop            | Type                                                                        | Description                                               |
| --------------- | --------------------------------------------------------------------------- | --------------------------------------------------------- |
| **`available`** | <code>boolean</code>                                                        | See the false-negative caution on `canOpenVendorSetting`. |
| **`matched`**   | <code><a href="#vendorsettingtarget">VendorSettingTarget</a> \| null</code> | The candidate that resolved, or `null`.                   |


#### VendorSettingOptions

| Prop             | Type                                        | Description                                                                                                                                                                                                                                      |
| ---------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`candidates`** | <code>readonly VendorSettingTarget[]</code> | Tried in order; the first that works wins. An empty list is an error. `readonly` so a list declared `as const` -- including the exported `KnownVendorSettingTargets` -- can be passed straight in without being copied. Nothing here mutates it. |


#### VendorSettingOpenResult

| Prop          | Type                                                                        | Description                                                    |
| ------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------- |
| **`opened`**  | <code>boolean</code>                                                        |                                                                |
| **`matched`** | <code><a href="#vendorsettingtarget">VendorSettingTarget</a> \| null</code> | The candidate that actually opened, or `null` when none would. |


#### DeviceDebugState

| Prop                          | Type                 | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ----------------------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`developerOptionsEnabled`** | <code>boolean</code> | True when Developer Options is enabled in the device settings (Settings.Global.DEVELOPMENT_SETTINGS_ENABLED on Android).                                                                                                                                                                                                                                                                                                                                                                                               |
| **`adbEnabled`**              | <code>boolean</code> | True when USB debugging / ADB is enabled (Settings.Global.ADB_ENABLED on Android).                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **`appDebuggable`**           | <code>boolean</code> | True when the running app itself is a debuggable build (ApplicationInfo.FLAG_DEBUGGABLE on Android — i.e. android:debuggable="true" in the manifest, as produced by a debug build type). This is independent of the device Developer Options / ADB settings: Stripe Terminal v5 also refuses production Tap to Pay from a debuggable app ("Debuggable applications are not supported when using the production version of the Tap to Pay reader"), which no device toggle can clear — only installing a release build. |
| **`anyDebugEnabled`**         | <code>boolean</code> | Convenience OR of the individual flags — true when any debug option is on.                                                                                                                                                                                                                                                                                                                                                                                                                                             |


#### MicrophonePermissionState

| Prop             | Type                                                                              | Description                                                                                                                                                                                                                                                                                                                                                                                                                               |
| ---------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`status`**     | <code><a href="#microphonepermissionstatus">MicrophonePermissionStatus</a></code> | The coarse state.                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **`canRequest`** | <code>boolean</code>                                                              | True when calling {@link NativeSettingsPlugin.requestMicrophonePermission} can still produce an OS dialog. This is the flag to gate an "Enable the microphone" button on. On Android this is true both before the first ask and after a plain Deny (Android allows one more attempt); it goes false once the user has chosen "Don't allow" twice. On iOS it is true only before the first ask — iOS never shows the dialog a second time. |
| **`blocked`**    | <code>boolean</code>                                                              | True when the permission is refused AND the OS will no longer offer a dialog, so the only remaining route is the app's settings screen. `blocked` is what a kiosk should surface as an operator-actionable error; `status === 'denied' && canRequest` is a diner-recoverable state and should NOT be escalated to the operator.                                                                                                           |


### Type Aliases


#### DeviceSettingScope

Which of Android's three settings tables to read.

`system` is where per-device user preferences live and is where vendor
feature toggles are usually found, so it is the default. `secure` and
`global` are readable too; many of their keys are documented platform
constants rather than vendor extras.

<code>(typeof DEVICE_SETTING_SCOPES)[number]</code>


#### VendorSettingTarget

One way to address a vendor settings screen: an intent action, or an explicit
component (package **and** activity together).

🔑 **A union rather than three optional fields, deliberately.** A half-specified
component -- `{ package }` with no `activity` -- cannot be launched, so the
native side skips it. As an interface with everything optional it would
compile cleanly and then do nothing at all, which is the most expensive kind
of mistake here: silent. The union makes it a compile error instead, and
leaves the runtime skip as a backstop for values built dynamically.

Both forms may carry the other's fields, so a candidate can name an action
*and* a component.

⚠️ **That is one intent, not a fallback pair.** The native side sets the
action and then the explicit component, and the component wins resolution --
it does not try the action first and fall back. Trying one form and then
another is what the `candidates` LIST does; a single entry naming both is a
single launch.

<code>{ /** Intent action, e.g. a vendor's own `...MOTION_SETTINGS` string. */ action: string; /** Package for an explicit component, e.g. `com.android.settings`. */ package?: string; /** * Fully-qualified activity for an explicit component. Inner-class * activities use `$`, e.g. `com.android.settings.Settings$SomeActivity`. */ activity?: string; } | { /** Intent action, e.g. a vendor's own `...MOTION_SETTINGS` string. */ action?: string; /** Package for an explicit component, e.g. `com.android.settings`. */ package: string; /** * Fully-qualified activity for an explicit component. Inner-class * activities use `$`, e.g. `com.android.settings.Settings$SomeActivity`. */ activity: string; }</code>


#### MicrophonePermissionStatus

`granted`     — the app may capture audio right now.
`prompt`      — never asked on this install; a request will show the dialog.
`denied`      — refused. Check `canRequest` to learn whether asking again
                would still put a dialog on screen.
`unsupported` — the platform has no such permission (web), or the permission
                is not declared in the app's manifest at all, in which case
                no amount of requesting will ever grant it.

<code>'granted' | 'prompt' | 'denied' | 'unsupported'</code>


### Enums


#### AndroidSettings

| Members                                | Value                                   | Description                                                                                                                                                   |
| -------------------------------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`Accessibility`**                    | <code>'accessibility'</code>            | Show settings for accessibility modules                                                                                                                       |
| **`Account`**                          | <code>'account'</code>                  | Show add account screen for creating a new account                                                                                                            |
| **`AirplaneMode`**                     | <code>'airplane_mode'</code>            | Show settings to allow entering/exiting airplane mode                                                                                                         |
| **`Apn`**                              | <code>'apn'</code>                      | Show settings to allow configuration of APNs                                                                                                                  |
| **`ApplicationDetails`**               | <code>'application_details'</code>      | Show screen of details about a particular application                                                                                                         |
| **`ApplicationDevelopment`**           | <code>'application_development'</code>  | Show settings to allow configuration of application development-related settings                                                                              |
| **`Application`**                      | <code>'application'</code>              | Show settings to allow configuration of application-related settings                                                                                          |
| **`AppNotification`**                  | <code>'app_notification'</code>         | Show settings to allow configuration of application-specific notifications                                                                                    |
| **`BatteryOptimization`**              | <code>'battery_optimization'</code>     | Show screen for controlling which apps can ignore battery optimizations                                                                                       |
| **`Bluetooth`**                        | <code>'bluetooth'</code>                | Show settings to allow configuration of Bluetooth                                                                                                             |
| **`Captioning`**                       | <code>'captioning'</code>               | Show settings for video captioning                                                                                                                            |
| **`Cast`**                             | <code>'cast'</code>                     | Show settings to allow configuration of cast endpoints                                                                                                        |
| **`DataRoaming`**                      | <code>'data_roaming'</code>             | Show settings for selection of 2G/3G/4G                                                                                                                       |
| **`Date`**                             | <code>'date'</code>                     | Show settings to allow configuration of date and time                                                                                                         |
| **`Display`**                          | <code>'display'</code>                  | Show settings to allow configuration of display                                                                                                               |
| **`Dream`**                            | <code>'dream'</code>                    | Show Daydream settings                                                                                                                                        |
| **`Home`**                             | <code>'home'</code>                     | Show Home selection settings                                                                                                                                  |
| **`Keyboard`**                         | <code>'keyboard'</code>                 | Show settings to configure input methods, in particular allowing the user to enable input methods                                                             |
| **`KeyboardSubType`**                  | <code>'keyboard_subtype'</code>         | Show settings to enable/disable input method subtypes                                                                                                         |
| **`Locale`**                           | <code>'locale'</code>                   | Show settings to allow configuration of locale                                                                                                                |
| **`Location`**                         | <code>'location'</code>                 | Show settings to allow configuration of current location sources                                                                                              |
| **`ManageApplications`**               | <code>'manage_applications'</code>      | Show settings to manage installed applications                                                                                                                |
| **`ManageAllApplications`**            | <code>'manage_all_applications'</code>  | Show settings to manage all applications                                                                                                                      |
| **`MemoryCard`**                       | <code>'memory_card'</code>              | Show settings for memory card storage                                                                                                                         |
| **`Network`**                          | <code>'network'</code>                  | Show settings for selecting the network operator                                                                                                              |
| **`NfcSharing`**                       | <code>'nfcsharing'</code>               | Show NFC Sharing settings                                                                                                                                     |
| **`NfcPayment`**                       | <code>'nfc_payment'</code>              | Show NFC Tap & Pay settings                                                                                                                                   |
| **`NfcSettings`**                      | <code>'nfc_settings'</code>             | Show NFC settings                                                                                                                                             |
| **`Print`**                            | <code>'print'</code>                    | Show the top level print settings                                                                                                                             |
| **`Privacy`**                          | <code>'privacy'</code>                  | Show settings to allow configuration of privacy options                                                                                                       |
| **`QuickLaunch`**                      | <code>'quick_launch'</code>             | Show settings to allow configuration of quick launch shortcuts                                                                                                |
| **`Search`**                           | <code>'search'</code>                   | Show settings for global search                                                                                                                               |
| **`Security`**                         | <code>'security'</code>                 | Show settings to allow configuration of security and location privacy                                                                                         |
| **`Settings`**                         | <code>'settings'</code>                 | Show system settings                                                                                                                                          |
| **`ShowRegulatoryInfo`**               | <code>'show_regulatory_info'</code>     | Show the regulatory information screen for the device                                                                                                         |
| **`Sound`**                            | <code>'sound'</code>                    | Show settings to a llow configuration of sound and volume                                                                                                     |
| **`Storage`**                          | <code>'storage'</code>                  | Show settings for internal storage                                                                                                                            |
| **`Sync`**                             | <code>'sync'</code>                     | Show settings to allow configuration of sync settings                                                                                                         |
| **`TextToSpeech`**                     | <code>'text_to_speech'</code>           | Show settings for configuring Text-to-Speech (TTS) output                                                                                                     |
| **`Usage`**                            | <code>'usage'</code>                    | Show settings to control access to usage information                                                                                                          |
| **`UserDictionary`**                   | <code>'user_dictionary'</code>          | Show settings to manage the user input dictionary                                                                                                             |
| **`VoiceInput`**                       | <code>'voice_input'</code>              | Show settings to configure input methods, in particular allowing the user to enable input methods                                                             |
| **`VPN`**                              | <code>'vpn'</code>                      | Show settings to allow configuration of VPN                                                                                                                   |
| **`Wifi`**                             | <code>'wifi'</code>                     | Show settings to allow configuration of Wi-Fi                                                                                                                 |
| **`WifiIp`**                           | <code>'wifi_ip'</code>                  | Show settings to allow configuration of a static IP address for Wi-Fi                                                                                         |
| **`Wireless`**                         | <code>'wireless'</code>                 | Show settings to allow configuration of wireless controls such as Wi-Fi, Bluetooth and Mobile networks                                                        |
| **`ConnectedDeviceDashboardActivity`** | <code>'connected_devices'</code>        | Show connected devices                                                                                                                                        |
| **`ZenMode`**                          | <code>'zen_mode'</code>                 | Zen mode settings.                                                                                                                                            |
| **`ZenModePriority`**                  | <code>'zen_mode_priority'</code>        | Zen mode priority settings. Note that this may not work on every single device. See: https://github.com/RaphaelWoude/capacitor-native-settings/pull/63        |
| **`ZenModeBlockedEffects`**            | <code>'zen_mode_blocked_effects'</code> | Zen mode blocked effects settings. Note that this may not work on every single device. See: https://github.com/RaphaelWoude/capacitor-native-settings/pull/63 |


#### IOSSettings

| Members                        | Value                                   | Description                                                                                                               |
| ------------------------------ | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| **`About`**                    | <code>'about'</code>                    | Settings &gt; About page                                                                                                  |
| **`App`**                      | <code>'app'</code>                      | Opens your app-specific settings screen. Note that this is the only officially supported settings screen by Apple.        |
| **`AppNotification`**          | <code>'appNotification'</code>          | Opens app-specific notification settings screen for iOS 15.4+; opens general app-specific settings for earlier versions." |
| **`AutoLock`**                 | <code>'autoLock'</code>                 | Used to set if and when the screen should be automatically locked.                                                        |
| **`Bluetooth`**                | <code>'bluetooth'</code>                | Bluetooth settings. Allows the users to enable/disable bluetooth and to search for devices.                               |
| **`LocationCheckPermission`**  | <code>'locationCheckPermission'</code>  | Check Location permission.                                                                                                |
| **`BluetoothCheckPermission`** | <code>'bluetoothCheckPermission'</code> | Check Bluetooth permission.                                                                                               |
| **`BluetoothCheckPowerOn`**    | <code>'bluetoothCheckPowerOn'</code>    | Check whether Bluetooth is turned on.                                                                                     |
| **`DateTime`**                 | <code>'dateTime'</code>                 | Date and time settings.                                                                                                   |
| **`FaceTime`**                 | <code>'facetime'</code>                 | FaceTime settings.                                                                                                        |
| **`General`**                  | <code>'general'</code>                  | Opens iOS general settings screen.                                                                                        |
| **`Keyboard`**                 | <code>'keyboard'</code>                 | Keyboard settings.                                                                                                        |
| **`ICloud`**                   | <code>'iCloud'</code>                   | iCloud settings.                                                                                                          |
| **`ICloudStorageBackup`**      | <code>'iCloudStorageBackup'</code>      | iCloud Storage and Backup settings.                                                                                       |
| **`International`**            | <code>'international'</code>            | Language and region settings.                                                                                             |
| **`LocationServices`**         | <code>'locationServices'</code>         | Show settings to allow configuration of current location sources                                                          |
| **`Music`**                    | <code>'music'</code>                    | Music settings.                                                                                                           |
| **`Notes`**                    | <code>'notes'</code>                    | Notes settings.                                                                                                           |
| **`Notifications`**            | <code>'notifications'</code>            | Notifications settings.                                                                                                   |
| **`Phone`**                    | <code>'phone'</code>                    | Phone settings.                                                                                                           |
| **`Photos`**                   | <code>'photos'</code>                   | Photos settings.                                                                                                          |
| **`ManagedConfigurationList`** | <code>'managedConfigurationList'</code> | Allows the user to manage configuration profiles that are installed on the phone.                                         |
| **`Reset`**                    | <code>'reset'</code>                    | Screen where the user can reset the phone to factory settings.                                                            |
| **`Ringtone`**                 | <code>'ringtone'</code>                 | Ringtone settings.                                                                                                        |
| **`Sounds`**                   | <code>'sounds'</code>                   | Used to set phone volume, vibration settings, etc.                                                                        |
| **`SoftwareUpdate`**           | <code>'softwareUpdate'</code>           | Software update screen.                                                                                                   |
| **`Store`**                    | <code>'store'</code>                    | Store settings.                                                                                                           |
| **`Tracking`**                 | <code>'tracking'</code>                 | Tracking settings.                                                                                                        |
| **`VPN`**                      | <code>'vpn'</code>                      | VPN settings.                                                                                                             |
| **`Wallpaper`**                | <code>'wallpaper'</code>                | Wallpaper settings.                                                                                                       |
| **`WiFi`**                     | <code>'wifi'</code>                     | WiFi settings.                                                                                                            |
| **`Tethering`**                | <code>'tethering'</code>                | Tethering settings (used to create a hotspot with mobile data).                                                           |
| **`DoNotDisturb`**             | <code>'doNotDisturb'</code>             | Do Not Disturb settings.                                                                                                  |
| **`TouchIdPasscode`**          | <code>'touchIdPasscode'</code>          | Touch id passcode settings.                                                                                               |
| **`GuidedAccess`**             | <code>'guidedAccess'</code>             |                                                                                                                           |
| **`GuidedAccessAutoLockTime`** | <code>'guidedAccessAutoLockTime'</code> |                                                                                                                           |
| **`ScreenTime`**               | <code>'screenTime'</code>               | Screen Time settings.                                                                                                     |
| **`Accessibility`**            | <code>'accessibility'</code>            | Accessibility settings.                                                                                                   |

</docgen-api>
