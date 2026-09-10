import { WebPlugin } from '@capacitor/core';

import { DEVICE_SETTING_SCOPES } from './definitions';
import type {
  DeviceDebugState,
  DeviceSettingOptions,
  DeviceSettingResult,
  DeviceSettingScope,
  MicrophonePermissionState,
  NativeSettingsPlugin,
  VendorSettingOpenResult,
  VendorSettingOptions,
  VendorSettingProbeResult,
} from './definitions';

/**
 * Narrows an arbitrary string to the scope union, so the validated value can be
 * returned without a cast. A cast here would compile while letting a bad scope
 * through untouched -- the failure this check exists to prevent.
 */
function isDeviceSettingScope(value: string): value is DeviceSettingScope {
  return (DEVICE_SETTING_SCOPES as readonly string[]).includes(value);
}

export class NativeSettingsWeb extends WebPlugin implements NativeSettingsPlugin {
  /**
   * Open iOS & Android settings.
   * Not implemented for web!
   */
  async open(): Promise<{ status: boolean }> {
    return new Promise<any>((_resolve, reject) => {
      reject(new Error('Not implemented for web.'));
    });
  }

  /**
   * Open android settings.
   * Not implemented for web!
   */
  async openAndroid(): Promise<{ status: boolean }> {
    return new Promise<any>((_resolve, reject) => {
      reject(new Error('Not implemented for web.'));
    });
  }

  /**
   * Open iOS settings.
   * Not implemented for web!
   */
  async openIOS(): Promise<{ status: boolean }> {
    return new Promise<any>((_resolve, reject) => {
      reject(new Error('Not implemented for web.'));
    });
  }

  /**
   * Device debug state is an Android concept; on web there is nothing to
   * inspect, so report every flag as false rather than rejecting.
   */
  async getDebugState(): Promise<DeviceDebugState> {
    return {
      developerOptionsEnabled: false,
      adbEnabled: false,
      appDebuggable: false,
      anyDebugEnabled: false,
    };
  }

  /**
   * Best-effort read of the browser's microphone permission. The Permissions
   * API answers without prompting where it exists; where it does not, report
   * `prompt` rather than guessing, since a wrong `blocked` would hide the
   * enable button for no reason.
   */
  async checkMicrophonePermission(): Promise<MicrophonePermissionState> {
    try {
      const permissions = (navigator as any).permissions;
      if (permissions?.query) {
        const result = await permissions.query({ name: 'microphone' as PermissionName });
        if (result.state === 'granted') {
          return { status: 'granted', canRequest: false, blocked: false };
        }
        if (result.state === 'denied') {
          return { status: 'denied', canRequest: false, blocked: true };
        }
      }
    } catch {
      // Permissions API missing or 'microphone' unrecognised -- fall through.
    }
    return { status: 'prompt', canRequest: true, blocked: false };
  }

  /**
   * On the web the only way to ask is to open a capture and close it again.
   */
  async requestMicrophonePermission(): Promise<MicrophonePermissionState> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((track) => track.stop());
      return { status: 'granted', canRequest: false, blocked: false };
    } catch (error) {
      // Only a refusal is a refusal. NotFoundError (no microphone on this
      // machine), NotReadableError (device held by something else) and
      // AbortError are not the user saying no, and reporting them as `blocked`
      // would raise an operator alarm that no settings screen can clear.
      const name = (error as { name?: string })?.name;
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        return { status: 'denied', canRequest: false, blocked: true };
      }
      return { status: 'unsupported', canRequest: false, blocked: false };
    }
  }

  /**
   * The browser has no device settings table. Report the key as absent rather
   * than inventing a value: `present: false` is the honest answer and is the
   * same one a caller gets from an Android build that lacks the key.
   *
   * The argument checks mirror the native ones on purpose. A web-first
   * developer who gets a well-formed answer here for a bad key or a mistyped
   * scope would only meet the rejection later, on a device -- so the platform
   * that is easiest to develop against must not be the most forgiving one.
   */
  async getDeviceSetting(options: DeviceSettingOptions): Promise<DeviceSettingResult> {
    if (!options?.key || options.key.trim().length === 0) {
      throw new Error('getDeviceSetting requires a key');
    }
    const scope = (options.scope ?? 'system').trim().toLowerCase();
    if (!isDeviceSettingScope(scope)) {
      throw new Error('getDeviceSetting scope must be one of: system, secure, global');
    }
    return {
      key: options.key,
      scope,
      value: null,
      present: false,
    };
  }

  /**
   * No vendor settings screens exist on the web. An empty candidate list is
   * still rejected, as it is natively -- it is a caller mistake on every
   * platform, and only the native ones would otherwise say so.
   */
  async canOpenVendorSetting(options: VendorSettingOptions): Promise<VendorSettingProbeResult> {
    if (!options?.candidates || options.candidates.length === 0) {
      throw new Error('canOpenVendorSetting requires a non-empty candidates array');
    }
    return { available: false, matched: null };
  }

  /** No vendor settings screens exist on the web. */
  async openVendorSetting(options: VendorSettingOptions): Promise<VendorSettingOpenResult> {
    if (!options?.candidates || options.candidates.length === 0) {
      throw new Error('openVendorSetting requires a non-empty candidates array');
    }
    return { opened: false, matched: null };
  }
}
