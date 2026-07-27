# Release keystore

`kai-master-tool.jks` is committed to this repository on purpose, and its
password is in `app/gradle.properties` in plain text.

## Why it is not secret

Android refuses to install an update whose signing certificate differs from the
installed app's. The in-app updater downloads an APK from GitHub and installs it
over the running build, so **every build must be signed with the same key** or
updating breaks and the app has to be uninstalled and reinstalled by hand —
losing its local decks and card database.

CI cannot use the automatic debug key: it is generated per machine, so each run
would produce a differently-signed APK.

The alternatives were a GitHub Actions secret (which makes local release builds
impossible and is easy to lose) or accepting a public key. For a personal tool
distributed through this repository's releases, public is the reasonable trade.

## What this key does and does not protect

It **does** guarantee that an update is byte-compatible with the installed app,
and that Android's own update path works.

It **does not** authenticate the publisher. Anyone with this repository can sign
an APK that your device will accept as an update to `com.kaiharimoto.mastertool`.
That only matters if someone gets such an APK in front of you — the app only
ever downloads from this repository's GitHub releases over HTTPS, so the
realistic risk is installing an APK you were handed from somewhere else.

**Do not reuse this key for anything that matters**, and do not publish this app
to Google Play with it: Play upload keys must be private.

## Details

| | |
|---|---|
| File | `androidApp/keystore/kai-master-tool.jks` (PKCS#12) |
| Alias | `kai-master-tool` |
| Password | `kaimastertool` (both store and key) |
| Algorithm | RSA 4096, SHA384withRSA |
| Valid until | 2076 |
| SHA-256 | `0bb9cc076e3ac23330e27c87711ec34ca917d8c2471385c6a6b550e1301cb22c` |

The release workflow verifies that digest on every published APK and fails the
release if it does not match.

## Rotating it

Don't, unless you accept that everyone has to uninstall and reinstall. If you
must:

```bash
keytool -genkeypair -v -keystore kai-master-tool.jks -storetype PKCS12 \
  -alias kai-master-tool -keyalg RSA -keysize 4096 -validity 18250 \
  -storepass <password> -keypass <password> \
  -dname "CN=kai's master tool, OU=kaiharimoto, O=kaiharimoto, C=US"
```

Then update the password in `app/gradle.properties` and `EXPECTED_SHA256` in
`.github/workflows/release.yml`:

```bash
keytool -exportcert -keystore kai-master-tool.jks -alias kai-master-tool \
  -storepass <password> | sha256sum
```
