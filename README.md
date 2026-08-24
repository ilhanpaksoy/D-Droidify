# D-Droidify

Fork of [Droid-ify](https://github.com/Iamlooker/Droid-ify)

This fork adds **Dhizuku** silent-install support. Can be installed and tested side-by-side with upstream app. Can self-update itself.

- **Upstream:** `com.looker.droidify`
- **Package:** `io.github.ilhan.droidify` 
- **Changes:** Dhizuku installer (`DhizukuInstaller` + `HiddenApiBypass`), `applicationId` rebrand, app logo rebrand. See `NOTICE.md`.
- **F-Droid Repo:** `https://ilhanpaksoy.github.io/client/fdroid/repo`
- **APK Signature:** `19:10:65:6D:26:98:6A:20:8D:7D:CB:5D:9C:5A:B0:55:0A:D7:C2:E1:51:C8:1E:CC:36:EE:F0:C8:CB:B8:C1:D1`
- **Repo Fingerprint:** `B9:C9:B4:0A:F2:15:22:E1:F3:3C:BB:CB:D6:69:8C:AB:AF:F4:93:30:88:86:9D:75:0E:AF:78:E2:6A:7C:B1:91`

## Download

[GitHub Releases](https://github.com/ilhanpaksoy/client/releases) · [F-Droid Repo](https://ilhanpaksoy.github.io/client/fdroid/repo)

## Build

Create `keystore.properties` in repo root:
```
storeFile=d-droidify.jks
storePassword=***
keyAlias=d-droidify
keyPassword=***
```
```sh
./gradlew assembleRelease
```

License: GPL-3.0-or-later. See `LICENSE`.
