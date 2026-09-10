import { VendorSettingTarget } from './definitions';

/**
 * Vendor settings screens that have been OBSERVED to open, offered as data.
 *
 * 🔑 **One declaration is the data, its validation, and its type.** `satisfies`
 * checks every entry against {@link VendorSettingTarget} where it is written --
 * so a component missing its `activity` fails to compile here rather than being
 * silently skipped on a device -- and `as const` keeps the literal types, which
 * is what makes {@link KnownVendorSettingName} derive from the list instead of
 * being maintained alongside it. Adding a screen is one edit.
 *
 * ⚠️ **This is data, not a stable API.** Each entry was seen working on ONE
 * device, on ONE firmware build, on the date noted. OEM screens move between
 * builds. Treat a miss as expected: pass your own candidates when you know
 * better, and always pair an offer with a {@link
 * NativeSettingsPlugin.getDeviceSetting} read, so no one is sent to a screen for
 * a feature their device does not have. A changed string here is a minor
 * version bump, never a patch.
 */
export const KnownVendorSettingTargets = {
  /**
   * The "Motions and gestures" screen, where a double-tap-to-wake toggle lives
   * on devices that have one.
   *
   * 🔴 **The order is load-bearing, and the reason is a trap.** The explicit
   * component is FIRST because the action form is worse than useless on at
   * least one build: verified 2026-09-08 on an Android 14 tablet, launching the
   * action *succeeded* and landed the user on the Settings **home page** rather
   * than on the motions screen. It resolves, it starts, it reports success --
   * and it goes somewhere else. The component form landed exactly right on the
   * same device. Since the first candidate that starts wins, an action listed
   * ahead of a component means the component is never tried.
   */
  motionAndGestureSettings: [
    { package: 'com.android.settings', activity: 'com.android.settings.Settings$MotionAndGestureSettingsActivity' },
    { action: 'com.samsung.settings.MOTION_AND_GESTURE_SETTINGS' },
  ],
} as const satisfies Record<string, readonly VendorSettingTarget[]>;

/**
 * The names in {@link KnownVendorSettingTargets}, derived from the constant so
 * the two can never drift apart.
 */
export type KnownVendorSettingName = keyof typeof KnownVendorSettingTargets;
