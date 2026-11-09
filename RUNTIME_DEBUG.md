# Runtime debugging status

**Device verification is incomplete.** The APK and the five-test instrumentation suite compile successfully, and all 16 JVM unit tests pass. Android lint reports zero errors. These results do not establish successful device playback or end-to-end provider functionality.

Two Android 11 / API 30 software-emulator configurations were attempted. This environment has no /dev/kvm. Android's system process failed or became unresponsive before Mutiny Media could be installed and launched. The final install attempt returned `cmd: Failure calling service package: Broken pipe (32)`. The emulator was stopped. This is an emulator/OS failure; no Mutiny Media app crash was reproduced and no device test passed.

The playback review corrected Media3's data-source setup: the HTTP source with provider headers is now wrapped in DefaultDataSource.Factory, retaining Media3's standard internal resource routing. The app's source-selection gate still requires HTTPS.

## Run device checks

Use a disposable Android device/emulator with USB debugging and a working adb connection:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The suite covers fixture catalog/watchlist navigation, missing-key handling, settings recreation/persistence, Keystore encryption/clearing, DownloadManager queue submission, local MP4 playback with lifecycle/Fit controls, and optional game discovery/launch. It restores the previous encrypted key value, bandwidth setting and reserved watchlist fixture after each test. The local video test injects a fixture into the player; it does not verify HTTPS provider playback. Download completion and real OpenRouter requests are not asserted.

To enable the optional game test, set ANDROID_HOME to your SDK, then run:

```sh
./gradlew -p test-fixtures/game-app assembleDebug
adb install -r test-fixtures/game-app/build/outputs/apk/debug/MutinyGameFixture-debug.apk
./gradlew :app:connectedDebugAndroidTest
```

The fixture is a separate, explicitly labelled test app. The game test is skipped if it is absent. See DEVICE_CHECKLIST.md for the remaining manual and real-provider checks. No credentials, private signing keys or local SDK paths are included in this repository.
