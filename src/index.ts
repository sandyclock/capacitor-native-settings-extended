import { registerPlugin } from '@capacitor/core';

import type { NativeSettingsPlugin } from './definitions';

const NativeSettings = registerPlugin<NativeSettingsPlugin>('NativeSettings', {
  web: () => import('./web').then((m) => new m.NativeSettingsWeb()),
});

export * from './definitions';
// Named, never `export *`: this is documented data with no compatibility
// promise, and a barrel would put it on the public surface without ever saying
// so in the README. See the caution on the constant itself.
export { KnownVendorSettingTargets } from './known-targets';
export type { KnownVendorSettingName } from './known-targets';
export { NativeSettings };
