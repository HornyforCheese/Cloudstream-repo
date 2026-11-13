# CloudStream test build

`MutinyMedia_APK.zip` contains the debug application, instrumentation APK and legal
CloudStream fixture. `MutinyMedia_Android_Source_Fixed.zip` contains corresponding
source. See `MutinyMedia_SHA256SUMS.txt` for integrity hashes.

Install the app and use Settings → Integrations → CloudStream. To test the legal
fixture, paste this direct URL into the extension URL field:

https://raw.githubusercontent.com/HornyforCheese/Cloudstream-repo/fix/android-stremio-manifest-loading/downloads/cloudstream-legal-fixture.cs3

Search `bunny` for a movie or `series` for a title with season 2, episode 3. The
fixture extracts Google's public Big Buck Bunny MP4. Its metadata is deterministic
test data; it does not certify any external provider's live compatibility.

For automatic physical-device tests, install the app and test APK with adb, then:

```sh
adb shell am instrument -w -e class com.mutinymedia.android.CloudStreamRuntimeTest com.mutinymedia.android.test/androidx.test.runner.AndroidJUnitRunner
```

Or build from source and run `./gradlew :app:connectedDebugAndroidTest` with a
physical device connected. No emulation was performed. See the included runtime
report for limitations and manual regression checks. This build contains GPL-3.0
CloudStream source; preserve its license and corresponding source when sharing.
