import Foundation
import Capacitor
import CoreLocation
import CoreBluetooth
import AVFoundation


@objc(NativeSettingsPlugin)
public class NativeSettingsPlugin: CAPPlugin, CAPBridgedPlugin, CBCentralManagerDelegate {

    /// The unique identifier for the plugin.
    public let identifier = "NativeSettingsPlugin"

    /// The name used to reference this plugin in JavaScript.
    public let jsName = "NativeSettings"

    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "openIOS", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDebugState", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkMicrophonePermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestMicrophonePermission", returnType: CAPPluginReturnPromise)
    ]

    let settingsPaths = [
        "about": "App-prefs:General&path=About",
        "autoLock": "App-prefs:General&path=AUTOLOCK",
        "bluetooth": "App-prefs:Bluetooth",
        "dateTime": "App-prefs:General&path=DATE_AND_TIME",
        "facetime": "App-prefs:FACETIME",
        "general": "App-prefs:General",
        "keyboard": "App-prefs:General&path=Keyboard",
        "iCloud": "App-prefs:CASTLE",
        "iCloudStorageBackup": "App-prefs:CASTLE&path=STORAGE_AND_BACKUP",
        "international": "App-prefs:General&path=INTERNATIONAL",
        "locationServices": "App-prefs:Privacy&path=LOCATION",
        "music": "App-prefs:MUSIC",
        "notes": "App-prefs:NOTES",
        "notifications": "App-prefs:NOTIFICATIONS_ID",
        "phone": "App-prefs:Phone",
        "photos": "App-prefs:Photos",
        "managedConfigurationList": "App-prefs:General&path=ManagedConfigurationList",
        "reset": "App-prefs:General&path=Reset",
        "ringtone": "App-prefs:Sounds&path=Ringtone",
        "sounds": "App-prefs:Sounds",
        "softwareUpdate": "App-prefs:General&path=SOFTWARE_UPDATE_LINK",
        "store": "App-prefs:STORE",
        "tracking": "App-prefs:Privacy&path=USER_TRACKING",
        "wallpaper": "App-prefs:Wallpaper",
        "wifi": "App-prefs:WIFI",
        "tethering": "App-prefs:INTERNET_TETHERING",
        "doNotDisturb": "App-prefs:DO_NOT_DISTURB",
        "touchIdPasscode": "App-prefs:TOUCHID_PASSCODE",
        "guidedAccess": "App-prefs:ACCESSIBILITY&path=GUIDED_ACCESS_TITLE",
        "guidedAccessAutoLockTime": "App-prefs:ACCESSIBILITY&path=GUIDED_ACCESS_TITLE/GuidedAccessAutoLockTime",
        "screenTime": "App-prefs:SCREEN_TIME",
        "accessibility": "App-prefs:ACCESSIBILITY",
        "vpn": "App-prefs:VPN"
    ]

  var _call: CAPPluginCall?=nil;
  
  var _manager: CBCentralManager!;
  
  @objc public func centralManagerDidUpdateState(_ central: CBCentralManager) {
      switch central.state {
      case .poweredOff:
        _call?.resolve(["status":false]);
        _call=nil;
        return;
      default: break
      }
    _call?.resolve(["status":true]);

  }

    @objc func open(_ call: CAPPluginCall) {
        let option = call.getString("optionIOS") ?? ""
        handleOpen(call: call, option: option)
    }

    @objc func openIOS(_ call: CAPPluginCall) {
        let option = call.getString("option") ?? ""
        handleOpen(call: call, option: option)
    }

    /// Device debug state is an Android concept (Developer Options / ADB) that
    /// gates Stripe Terminal v5 Tap to Pay. iOS has no equivalent device flag,
    /// so resolve every flag as false rather than rejecting.
    @objc func getDebugState(_ call: CAPPluginCall) {
        call.resolve([
            "developerOptionsEnabled": false,
            "adbEnabled": false,
            "appDebuggable": false,
            "anyDebugEnabled": false
        ])
    }

    /// Reports the microphone permission state without ever showing a dialog.
    @objc func checkMicrophonePermission(_ call: CAPPluginCall) {
        call.resolve(microphoneState())
    }

    /// Requests the microphone permission and resolves with the resulting state.
    ///
    /// iOS shows this dialog at most once per install: after a refusal the
    /// completion handler fires immediately with `false` and nothing appears on
    /// screen. That is why `blocked` and `denied` coincide on iOS while they are
    /// distinct on Android.
    ///
    /// 🔴 The host app's Info.plist MUST carry NSMicrophoneUsageDescription.
    /// Requesting without it terminates the process — an iOS rule, not ours.
    @objc func requestMicrophonePermission(_ call: CAPPluginCall) {
        /*
         * iOS TERMINATES the process when a microphone request is made without
         * NSMicrophoneUsageDescription in the host app's Info.plist. Reject with
         * something a developer can read instead of handing them a crash with no
         * stack in it.
         *
         * Only missing-or-empty is rejected. A present-but-stale string is a
         * consumer copy problem, not a crash -- iOS still shows the dialog, and
         * refusing to ask would break a working app over wording.
         */
        let usage = Bundle.main.object(forInfoDictionaryKey: "NSMicrophoneUsageDescription") as? String
        if (usage ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            call.reject("NSMicrophoneUsageDescription is missing from Info.plist. iOS terminates the app if the microphone is requested without it.")
            return
        }

        if #available(iOS 17.0, *) {
            AVAudioApplication.requestRecordPermission { [weak self] _ in
                DispatchQueue.main.async {
                    call.resolve(self?.microphoneState() ?? NativeSettingsPlugin.unsupportedMicrophoneState)
                }
            }
        } else {
            AVAudioSession.sharedInstance().requestRecordPermission { [weak self] _ in
                DispatchQueue.main.async {
                    call.resolve(self?.microphoneState() ?? NativeSettingsPlugin.unsupportedMicrophoneState)
                }
            }
        }
    }

    private static let unsupportedMicrophoneState: [String: Any] = [
        "status": "unsupported",
        "canRequest": false,
        "blocked": false
    ]

    private func microphoneState() -> [String: Any] {
        var status = "unsupported"

        if #available(iOS 17.0, *) {
            switch AVAudioApplication.shared.recordPermission {
            case .granted: status = "granted"
            case .denied: status = "denied"
            case .undetermined: status = "prompt"
            @unknown default: status = "unsupported"
            }
        } else {
            switch AVAudioSession.sharedInstance().recordPermission {
            case .granted: status = "granted"
            case .denied: status = "denied"
            case .undetermined: status = "prompt"
            @unknown default: status = "unsupported"
            }
        }

        return [
            "status": status,
            // iOS offers the dialog once and only once, so the only state from
            // which a request can still raise one is 'prompt'.
            "canRequest": status == "prompt",
            "blocked": status == "denied"
        ]
    }

    @objc private func handleOpen(call: CAPPluginCall, option: String) {
        var settingsUrl: URL?

        if let path = settingsPaths[option], let url = URL(string: path) {
            settingsUrl = url
        } else if option == "app" {
            settingsUrl = URL(string: UIApplication.openSettingsURLString)
        } else if option == "locationCheckPermission"{
          if CLLocationManager.locationServicesEnabled() {
              switch CLLocationManager.authorizationStatus() {
                  case .notDetermined, .restricted, .denied:
                    call.resolve(["status": false]);
                    return;
                  case .authorizedAlways, .authorizedWhenInUse:
                    call.resolve(["status": true]);
                    return;
                  @unknown default:
                      break
              }
        } else if option == "appNotification" {
            if #available(iOS 16.0, *) {
                settingsUrl = URL(string: UIApplication.openNotificationSettingsURLString)
            } else {
                settingsUrl = URL(string: UIApplication.openSettingsURLString)
            }
        } else {
              print("Location services are not enabled")
          }

          call.resolve(["status": false]);
          return;
          
        }
      else if option == "bluetoothCheckPermission" {
        if #available(iOS 13.1, *) {
          let _retVal = CBCentralManager.authorization == .allowedAlways
          call.resolve(["status": _retVal]);
          return;
        }
        if #available(iOS 13.0, *) {
          let _retVal = CBCentralManager().authorization == .allowedAlways
          call.resolve(["status": _retVal]);
          return;
        }
        call.resolve(
          ["status":true]
        )
        return
      }
      else if option == "bluetoothCheckPowerOn" {
        _manager = CBCentralManager(delegate:self, queue:nil, options: [CBCentralManagerOptionShowPowerAlertKey: false]);
        _call = call;
        return
      }

         else {
            call.reject("Requested setting \"" + option + "\" is not available on iOS.")
            return
        }

        guard let validUrl = settingsUrl, UIApplication.shared.canOpenURL(validUrl) else {
            call.reject("Cannot open settings or invalid URL")
            return
        }

        DispatchQueue.main.async {
            UIApplication.shared.open(validUrl) { success in
                if success {
                    call.resolve(["status": success])
                } else {
                    call.reject("Failed to open settings")
                }
            }
        }
    }
}
