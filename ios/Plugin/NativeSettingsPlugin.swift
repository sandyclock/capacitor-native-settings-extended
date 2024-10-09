import Foundation
import Capacitor
import CoreLocation
import CoreBluetooth


@objc(NativeSettingsPlugin)

/*
 * https://github.com/FifiTheBulldog/ios-settings-urls/blob/master/settings-urls.md
 */
public class NativeSettingsPlugin: CAPPlugin, CBCentralManagerDelegate {
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
        "guidedAccessAutoLockTime": "App-prefs:ACCESSIBILITY&path=GUIDED_ACCESS_TITLE/GuidedAccessAutoLockTime"
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
    
    @objc private func handleOpen(call: CAPPluginCall, option: String) {
        var settingsUrl: URL!

        if settingsPaths[option] != nil {
            settingsUrl = URL(string: settingsPaths[option]!)
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

        DispatchQueue.main.async {
            if UIApplication.shared.canOpenURL(settingsUrl) {
                UIApplication.shared.open(settingsUrl, completionHandler: { (success) in
                    call.resolve([
                        "status": success
                    ])
                })
            } else {
                call.reject("Cannot open settings")
            }
        }
    }
}
