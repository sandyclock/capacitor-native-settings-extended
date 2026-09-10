export interface NativeSettingsPlugin {
  /**
   * Opens the specified options on android & ios.
   * Note that the only supported option by Apple is "App". Using other options
   * might break in future iOS versions or have your app rejected in the App Store.
   *
   * @param option PlatformOptions
   * @see PlatformOptions
   */
  open(option: PlatformOptions): Promise<{ status: boolean }>;

  /**
   * Opens the specified option in android.
   * Only use this if you have made sure the user is on android.
   * This can be done by checking the platform before hand.
   *
   * @param option AndroidOptions
   * @see AndroidOptions
   */
  openAndroid(option: AndroidOptions): Promise<{ status: boolean }>;

  /**
   * Opens the specified option on iOS.
   * Only use this if you have made sure the user is on iOS.
   * This can be done by checking the platform before hand.
   *
   * Note that the only supported option by Apple is "App". Using other options
   * might break in future iOS versions or have your app rejected in the App Store.
   *
   * @param option IOSOptions
   * @see IOSOptions
   */
  openIOS(option: IOSOptions): Promise<{ status: boolean }>;

  /**
   * Reports whether the device currently has debug-oriented options enabled
   * (Developer Options and/or ADB). Android only — on iOS and web every flag
   * resolves to false.
   *
   * This exists so apps can detect the states that make Stripe Terminal v5
   * refuse production Tap to Pay, and surface a clear instruction up front
   * instead of letting discovery silently time out. Two distinct causes are
   * reported: the device Developer Options / USB-Wi-Fi debugging being on
   * (fails discovery with TAP_TO_PAY_INSECURE_ENVIRONMENT), and the app itself
   * being a debuggable build (see {@link DeviceDebugState.appDebuggable}).
   *
   * @see DeviceDebugState
   */
  /**
   * Reads ONE device setting by name and reports its raw value. Android only —
   * on iOS and web it resolves `present: false, value: null`.
   *
   * 🔑 The value is returned as a RAW STRING, deliberately, because the useful
   * answer here is three-state and a boolean would destroy it:
   *
   * - `"1"`  — the setting exists and is on
   * - `"0"`  — the setting exists and is OFF (the user can turn it on)
   * - `null` — the key is not present: this build has no such feature at all,
   *            and no trip to any settings screen will ever create it
   *
   * The middle state is the actionable one and the one a boolean API loses. A
   * caller that only wants "is it on" can compare to `"1"`; a caller deciding
   * whether to OFFER a settings shortcut needs to tell `"0"` from `null`.
   *
   * Reading needs NO permission and does no I/O worth caching around. Writing
   * would need `WRITE_SETTINGS` and is deliberately NOT offered: these keys are
   * the user's, and a kiosk silently changing them is the behaviour this plugin
   * exists to avoid.
   *
   * ⚠️ Vendor keys are not API. Names differ between OEMs, appear and disappear
   * between builds of the same brand, and are not documented anywhere. `null`
   * is therefore an ordinary answer, not an error — see
   * {@link NativeSettingsPlugin.canOpenVendorSetting} for the matching caution
   * about opening one.
   *
   * @see DeviceSettingResult
   */
  getDeviceSetting(options: DeviceSettingOptions): Promise<DeviceSettingResult>;

  /**
   * Reports whether any of the supplied targets can be opened on this device,
   * WITHOUT opening anything. Android only.
   *
   * Candidates are tried in order and the first that resolves is reported, so
   * a caller can pass its best guess first and fall back to a broader screen.
   *
   * 🔴 A `false` here is weaker evidence than it looks, and callers must treat
   * it as "probably not" rather than "definitely not". Since Android 11 package
   * visibility can hide an activity that genuinely exists, this probe can be a
   * FALSE NEGATIVE unless the app declares the relevant intents in a `<queries>`
   * element of its manifest. {@link NativeSettingsPlugin.openVendorSetting}
   * therefore attempts each candidate for real rather than trusting this — use
   * this method to decide whether to show a button, and let the open attempt be
   * the final word.
   *
   * @see VendorSettingTarget
   */
  canOpenVendorSetting(options: VendorSettingOptions): Promise<VendorSettingProbeResult>;

  /**
   * Opens the first of the supplied targets that this device will accept.
   * Android only.
   *
   * 🔑 This does NOT pre-filter on
   * {@link NativeSettingsPlugin.canOpenVendorSetting}. Each candidate is
   * launched for real and a refusal is caught, so a target hidden from the
   * resolver by package visibility still opens. The trade is that a genuinely
   * absent target costs one caught exception per candidate, which is cheap and
   * happens once, in response to a tap.
   *
   * ⚠️ **This method holds no vendor knowledge.** It launches whatever the
   * caller supplies, exactly as {@link NativeSettingsPlugin.getDeviceSetting}
   * reads whatever key the caller names — OEM screens move between builds, and
   * baking component names into the native path would mean republishing the
   * plugin every time one moved.
   *
   * The package does ship {@link KnownVendorSettingTargets}: an optional,
   * separately exported list of screens that have been observed to open. It is
   * data a caller may pass, not behaviour this method applies, and it is
   * explicitly not a stable API — see its own caution. Nothing here consults
   * it, and a caller that ignores it loses nothing.
   *
   * 🔑 A candidate carrying **both** an action and a component is ONE intent,
   * not a fallback pair: the action is set, then the explicit component, and
   * the component wins resolution. Trying one form and then another is what the
   * `candidates` LIST does — order it most-specific first.
   *
   * 🔑 **This survives an armed kiosk, and the reason is the TASK.** The
   * activity is started from the host Activity's context with no
   * `FLAG_ACTIVITY_NEW_TASK`, so the settings screen joins the app's OWN task
   * rather than starting its own. A kiosk that re-front's itself on pause is
   * then a no-op: the task it pulls forward is already the frontmost one, and
   * the settings screen is on top of it. Verified 2026-09-08 on a contained
   * device — `isInKioskMode` true, the screen opened and stayed for the whole
   * observation rather than being pulled back.
   *
   * 🔴 **Do not add `FLAG_ACTIVITY_NEW_TASK` here.** It looks like tidying and
   * would give the settings screen its own task, which is exactly the shape a
   * containment re-front can pull back under. If a caller genuinely needs a
   * separate task, it should drop containment first, the way an app-permission
   * trip does.
   *
   * @see VendorSettingTarget
   */
  openVendorSetting(options: VendorSettingOptions): Promise<VendorSettingOpenResult>;

  getDebugState(): Promise<DeviceDebugState>;

  /**
   * Reports the current state of the microphone (RECORD_AUDIO) permission
   * WITHOUT ever showing a dialog. Safe to call on every screen.
   *
   * The point of this method is the distinction the platform APIs do not give
   * you directly: "never asked" versus "asked and permanently refused". A
   * customer-facing kiosk needs it, because a single diner who taps Deny turns
   * the voice agent off for every diner after them, and only a trip to the OS
   * settings screen can undo it.
   *
   * @see MicrophonePermissionState
   */
  checkMicrophonePermission(): Promise<MicrophonePermissionState>;

  /**
   * Requests the microphone permission, showing the OS dialog when the OS is
   * still willing to show it, and resolves with the state that resulted.
   *
   * This always goes to the OS, even when a previous
   * {@link NativeSettingsPlugin.checkMicrophonePermission} reported
   * `blocked: true`. Android silently resets permissions for unused apps, so a
   * cached "blocked" belief can be stale; a genuinely blocked permission simply
   * resolves denied with no dialog, which costs nothing.
   *
   * 🔴 iOS: the app's `Info.plist` MUST carry `NSMicrophoneUsageDescription`.
   * Requesting without it terminates the app — that is an iOS rule, not a
   * plugin behaviour.
   *
   * To send the user to the screen where a blocked permission can be restored,
   * call {@link NativeSettingsPlugin.open} with
   * `{ optionAndroid: AndroidSettings.ApplicationDetails, optionIOS: IOSSettings.App }`.
   * There is deliberately no separate method for it — that is the same screen.
   *
   * @see MicrophonePermissionState
   */
  requestMicrophonePermission(): Promise<MicrophonePermissionState>;
}

/**
 * `granted`     — the app may capture audio right now.
 * `prompt`      — never asked on this install; a request will show the dialog.
 * `denied`      — refused. Check `canRequest` to learn whether asking again
 *                 would still put a dialog on screen.
 * `unsupported` — the platform has no such permission (web), or the permission
 *                 is not declared in the app's manifest at all, in which case
 *                 no amount of requesting will ever grant it.
 */
export type MicrophonePermissionStatus = 'granted' | 'prompt' | 'denied' | 'unsupported';

export interface MicrophonePermissionState {
  /**
   * The coarse state.
   *
   * @see MicrophonePermissionStatus
   */
  status: MicrophonePermissionStatus;

  /**
   * True when calling {@link NativeSettingsPlugin.requestMicrophonePermission}
   * can still produce an OS dialog. This is the flag to gate an "Enable the
   * microphone" button on.
   *
   * On Android this is true both before the first ask and after a plain Deny
   * (Android allows one more attempt); it goes false once the user has chosen
   * "Don't allow" twice. On iOS it is true only before the first ask — iOS
   * never shows the dialog a second time.
   */
  canRequest: boolean;

  /**
   * True when the permission is refused AND the OS will no longer offer a
   * dialog, so the only remaining route is the app's settings screen.
   *
   * `blocked` is what a kiosk should surface as an operator-actionable error;
   * `status === 'denied' && canRequest` is a diner-recoverable state and should
   * NOT be escalated to the operator.
   */
  blocked: boolean;
}

export interface DeviceDebugState {
  /**
   * True when Developer Options is enabled in the device settings
   * (Settings.Global.DEVELOPMENT_SETTINGS_ENABLED on Android).
   */
  developerOptionsEnabled: boolean;

  /**
   * True when USB debugging / ADB is enabled
   * (Settings.Global.ADB_ENABLED on Android).
   */
  adbEnabled: boolean;

  /**
   * True when the running app itself is a debuggable build
   * (ApplicationInfo.FLAG_DEBUGGABLE on Android — i.e. android:debuggable="true"
   * in the manifest, as produced by a debug build type). This is independent of
   * the device Developer Options / ADB settings: Stripe Terminal v5 also refuses
   * production Tap to Pay from a debuggable app ("Debuggable applications are not
   * supported when using the production version of the Tap to Pay reader"),
   * which no device toggle can clear — only installing a release build.
   */
  appDebuggable: boolean;

  /**
   * Convenience OR of the individual flags — true when any debug option is on.
   */
  anyDebugEnabled: boolean;
}

export interface PlatformOptions {
  optionAndroid: AndroidSettings;
  optionIOS: IOSSettings;
}

export interface AndroidOptions {
  option: AndroidSettings;
}

export interface IOSOptions {
  option: IOSSettings;
}

export enum AndroidSettings {
  /**
   * Show settings for accessibility modules
   */
  Accessibility = 'accessibility',

  /**
   * Show add account screen for creating a new account
   */
  Account = 'account',

  /**
   * Show settings to allow entering/exiting airplane mode
   */
  AirplaneMode = 'airplane_mode',

  /**
   * Show settings to allow configuration of APNs
   */
  Apn = 'apn',

  /**
   * Show screen of details about a particular application
   */
  ApplicationDetails = 'application_details',

  /**
   * Show settings to allow configuration of application development-related settings
   */
  ApplicationDevelopment = 'application_development',

  /**
   * Show settings to allow configuration of application-related settings
   */
  Application = 'application',

  /**
   * Show settings to allow configuration of application-specific notifications
   */
  AppNotification = 'app_notification',

  /**
   * Show screen for controlling which apps can ignore battery optimizations
   */
  BatteryOptimization = 'battery_optimization',

  /**
   * Show settings to allow configuration of Bluetooth
   */
  Bluetooth = 'bluetooth',

  /**
   * Show settings for video captioning
   */
  Captioning = 'captioning',

  /**
   * Show settings to allow configuration of cast endpoints
   */
  Cast = 'cast',

  /**
   * Show settings for selection of 2G/3G/4G
   */
  DataRoaming = 'data_roaming',

  /**
   * Show settings to allow configuration of date and time
   */
  Date = 'date',

  /**
   * Show settings to allow configuration of display
   */
  Display = 'display',

  /**
   * Show Daydream settings
   */
  Dream = 'dream',

  /**
   * Show Home selection settings
   */
  Home = 'home',

  /**
   *    Show settings to configure input methods, in particular allowing the user to enable input methods
   */
  Keyboard = 'keyboard',

  /**
   * Show settings to enable/disable input method subtypes
   */
  KeyboardSubType = 'keyboard_subtype',

  /**
   * Show settings to allow configuration of locale
   */
  Locale = 'locale',

  /**
   * Show settings to allow configuration of current location sources
   */
  Location = 'location',

  /**
   *    Show settings to manage installed applications
   */
  ManageApplications = 'manage_applications',

  /**
   * Show settings to manage all applications
   */
  ManageAllApplications = 'manage_all_applications',

  /**
   * Show settings for memory card storage
   */
  MemoryCard = 'memory_card',

  /**
   * Show settings for selecting the network operator
   */
  Network = 'network',

  /**
   * Show NFC Sharing settings
   */
  NfcSharing = 'nfcsharing',

  /**
   * Show NFC Tap & Pay settings
   */
  NfcPayment = 'nfc_payment',

  /**
   * Show NFC settings
   */
  NfcSettings = 'nfc_settings',

  /**
   * Show the top level print settings
   */
  Print = 'print',

  /**
   * Show settings to allow configuration of privacy options
   */
  Privacy = 'privacy',

  /**
   * Show settings to allow configuration of quick launch shortcuts
   */
  QuickLaunch = 'quick_launch',

  /**
   * Show settings for global search
   */
  Search = 'search',

  /**
   * Show settings to allow configuration of security and location privacy
   */
  Security = 'security',

  /**
   * Show system settings
   */
  Settings = 'settings',

  /**
   * Show the regulatory information screen for the device
   */
  ShowRegulatoryInfo = 'show_regulatory_info',

  /**
   * Show settings to a llow configuration of sound and volume
   */
  Sound = 'sound',

  /**
   * Show settings for internal storage
   */
  Storage = 'storage',

  /**
   * Show settings to allow configuration of sync settings
   */
  Sync = 'sync',

  /**
   * Show settings for configuring Text-to-Speech (TTS) output
   */
  TextToSpeech = 'text_to_speech',

  /**
   * Show settings to control access to usage information
   */
  Usage = 'usage',

  /**
   * Show settings to manage the user input dictionary
   */
  UserDictionary = 'user_dictionary',

  /**
   * Show settings to configure input methods, in particular allowing the user to enable input methods
   */
  VoiceInput = 'voice_input',

  /**
   * Show settings to allow configuration of VPN
   */
  VPN = 'vpn',

  /**
   * Show settings to allow configuration of Wi-Fi
   */
  Wifi = 'wifi',

  /**
   * Show settings to allow configuration of a static IP address for Wi-Fi
   */
  WifiIp = 'wifi_ip',

  /**
   * Show settings to allow configuration of wireless controls such as Wi-Fi, Bluetooth and Mobile networks
   */
  Wireless = 'wireless',

  /**
   * Show connected devices
   */
  ConnectedDeviceDashboardActivity = 'connected_devices',

  /**
   * Zen mode settings.
   */
  ZenMode = 'zen_mode',

  /**
   * Zen mode priority settings.
   * Note that this may not work on every single device.
   * See: https://github.com/RaphaelWoude/capacitor-native-settings/pull/63
   */
  ZenModePriority = 'zen_mode_priority',

  /**
   * Zen mode blocked effects settings.
   * Note that this may not work on every single device.
   * See: https://github.com/RaphaelWoude/capacitor-native-settings/pull/63
   */
  ZenModeBlockedEffects = 'zen_mode_blocked_effects',
}

export enum IOSSettings {
  /**
   * Settings > About page
   */
  About = 'about',

  /**
   * Opens your app-specific settings screen. Note that this is the only officially supported settings screen by Apple.
   */
  App = 'app',

  /**
   * Opens app-specific notification settings screen for iOS 15.4+; opens general app-specific settings for earlier versions."
   */
  AppNotification = 'appNotification',

  /**
   * Used to set if and when the screen should be automatically locked.
   */
  AutoLock = 'autoLock',

  /**
   * Bluetooth settings. Allows the users to enable/disable bluetooth and to search for devices.
   */
  Bluetooth = 'bluetooth',

  /**
   * Check Location permission. 
   */
  LocationCheckPermission = "locationCheckPermission",

  /**
   * Check Bluetooth permission. 
   */
  BluetoothCheckPermission = "bluetoothCheckPermission",

  /**
   * Check whether Bluetooth is turned on. 
   */
  BluetoothCheckPowerOn = "bluetoothCheckPowerOn",

  /**
   * Date and time settings.
   */
  DateTime = 'dateTime',

  /**
   * FaceTime settings.
   */
  FaceTime = 'facetime',

  /**
   * Opens iOS general settings screen.
   */
  General = 'general',

  /**
   * Keyboard settings.
   */
  Keyboard = 'keyboard',

  /**
   * iCloud settings.
   */
  ICloud = 'iCloud',

  /**
   * iCloud Storage and Backup settings.
   */
  ICloudStorageBackup = 'iCloudStorageBackup',

  /**
   * Language and region settings.
   */
  International = 'international',

  /**
   * Show settings to allow configuration of current location sources
   */
  LocationServices = 'locationServices',

  /**
   * Music settings.
   */
  Music = 'music',

  /**
   * Notes settings.
   */
  Notes = 'notes',

  /**
   * Notifications settings.
   */
  Notifications = 'notifications',

  /**
   * Phone settings.
   */
  Phone = 'phone',

  /**
   * Photos settings.
   */
  Photos = 'photos',

  /**
   * Allows the user to manage configuration profiles that are installed on the phone.
   */
  ManagedConfigurationList = 'managedConfigurationList',

  /**
   * Screen where the user can reset the phone to factory settings.
   */
  Reset = 'reset',

  /**
   * Ringtone settings.
   */
  Ringtone = 'ringtone',

  /**
   * Used to set phone volume, vibration settings, etc.
   */
  Sounds = 'sounds',

  /**
   * Software update screen.
   */
  SoftwareUpdate = 'softwareUpdate',

  /**
   * Store settings.
   */
  Store = 'store',

  /**
   * Tracking settings.
   */
  Tracking = 'tracking',

  /**
   * VPN settings.
   */
  VPN = 'vpn',

  /**
   * Wallpaper settings.
   */
  Wallpaper = 'wallpaper',

  /**
   * WiFi settings.
   */
  WiFi = 'wifi',

  /**
   * Tethering settings (used to create a hotspot with mobile data).
   */
  Tethering = 'tethering',

  /**
   * Do Not Disturb settings.
   */
  DoNotDisturb = 'doNotDisturb',

  /**
   * Touch id passcode settings.
   */
  TouchIdPasscode = 'touchIdPasscode',

  GuidedAccess = "guidedAccess",

  GuidedAccessAutoLockTime = "guidedAccessAutoLockTime",
  
  /**
   * Screen Time settings.
   */
  ScreenTime = 'screenTime',

  /**
   * Accessibility settings.
   */
  Accessibility = 'accessibility'
}

/**
 * The settings tables, as a value so the type and the runtime check cannot
 * drift. A hand-written union plus a hand-written validator is two lists to
 * keep in step, and the failure is silent in the dangerous direction: widen the
 * type, forget the validator, and the new scope compiles at every call site
 * while being rejected at runtime.
 */
export const DEVICE_SETTING_SCOPES = ['system', 'secure', 'global'] as const;

/**
 * Which of Android's three settings tables to read.
 *
 * `system` is where per-device user preferences live and is where vendor
 * feature toggles are usually found, so it is the default. `secure` and
 * `global` are readable too; many of their keys are documented platform
 * constants rather than vendor extras.
 */
export type DeviceSettingScope = (typeof DEVICE_SETTING_SCOPES)[number];

export interface DeviceSettingOptions {
  /**
   * The setting name, exactly as the vendor spells it. Case-sensitive, and not
   * validated: an unknown name is answered `present: false`, which is
   * indistinguishable from a device that lacks the feature. Prefer a constant
   * in your own code over a literal at the call site.
   */
  key: string;

  /** Defaults to `system`. */
  scope?: DeviceSettingScope;
}

export interface DeviceSettingResult {
  /** Echoed back, so a caller batching reads can tell answers apart. */
  key: string;

  /** Echoed back — the scope actually read, with the default applied. */
  scope: DeviceSettingScope;

  /**
   * The raw stored value, or `null` when the key is not present.
   * See {@link NativeSettingsPlugin.getDeviceSetting} for why this is a string.
   */
  value: string | null;

  /**
   * `false` means the key does not exist on this build — the feature is absent,
   * not merely switched off. Equivalent to `value === null`, named so the
   * distinction is hard to skim past.
   */
  present: boolean;
}

/**
 * One way to address a vendor settings screen: an intent action, or an explicit
 * component (package **and** activity together).
 *
 * 🔑 **A union rather than three optional fields, deliberately.** A half-specified
 * component -- `{ package }` with no `activity` -- cannot be launched, so the
 * native side skips it. As an interface with everything optional it would
 * compile cleanly and then do nothing at all, which is the most expensive kind
 * of mistake here: silent. The union makes it a compile error instead, and
 * leaves the runtime skip as a backstop for values built dynamically.
 *
 * Both forms may carry the other's fields, so a candidate can name an action
 * *and* a component.
 *
 * ⚠️ **That is one intent, not a fallback pair.** The native side sets the
 * action and then the explicit component, and the component wins resolution --
 * it does not try the action first and fall back. Trying one form and then
 * another is what the `candidates` LIST does; a single entry naming both is a
 * single launch.
 */
export type VendorSettingTarget =
  | {
      /** Intent action, e.g. a vendor's own `...MOTION_SETTINGS` string. */
      action: string;
      /** Package for an explicit component, e.g. `com.android.settings`. */
      package?: string;
      /**
       * Fully-qualified activity for an explicit component. Inner-class
       * activities use `$`, e.g. `com.android.settings.Settings$SomeActivity`.
       */
      activity?: string;
    }
  | {
      /** Intent action, e.g. a vendor's own `...MOTION_SETTINGS` string. */
      action?: string;
      /** Package for an explicit component, e.g. `com.android.settings`. */
      package: string;
      /**
       * Fully-qualified activity for an explicit component. Inner-class
       * activities use `$`, e.g. `com.android.settings.Settings$SomeActivity`.
       */
      activity: string;
    };

export interface VendorSettingOptions {
  /**
   * Tried in order; the first that works wins. An empty list is an error.
   *
   * `readonly` so a list declared `as const` -- including the exported
   * `KnownVendorSettingTargets` -- can be passed straight in without being
   * copied. Nothing here mutates it.
   */
  candidates: readonly VendorSettingTarget[];
}

export interface VendorSettingProbeResult {
  /** See the false-negative caution on `canOpenVendorSetting`. */
  available: boolean;

  /** The candidate that resolved, or `null`. */
  matched: VendorSettingTarget | null;
}

export interface VendorSettingOpenResult {
  opened: boolean;

  /** The candidate that actually opened, or `null` when none would. */
  matched: VendorSettingTarget | null;
}
