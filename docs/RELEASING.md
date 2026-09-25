# Releasing

Every change merged into `main` publishes a new installable APK on the [Releases page](https://github.com/booned976/Nexus-Budget/releases). The download link in the README always points to the newest one:

```
https://github.com/booned976/Nexus-Budget/releases/latest/download/NexusBudget.apk
```

The [release workflow](../.github/workflows/release.yml) runs the tests, builds a minified release APK, and publishes it as `NexusBudget.apk` under a tag like `v0.1.12`. Changes that only touch documentation don't trigger a release. To publish a specific version number, push a tag such as `v1.0.0`. You can also run the workflow by hand from the **Actions** tab.

## Signing key (one-time setup)

Android only installs an update over an existing app when both are signed with the same key. So that people can update without uninstalling (which would erase their data), store one signing key as a repository secret:

1. Open the repository on GitHub and go to **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `SIGNING_KEY`
3. Value: the base64 text of a PKCS12 keystore (see below). Save.

Without the secret, releases are still published, but each one is signed with a temporary key and the release notes say so.

### Creating a keystore

With a JDK installed:

```bash
keytool -genkeypair -storetype PKCS12 -keystore nexusbudget.p12 -alias nexusbudget \
  -keyalg RSA -keysize 2048 -validity 36500 -storepass nexusbudget -dname "CN=Nexus Budget"
base64 -w0 nexusbudget.p12 > signing-key.txt   # on macOS: base64 -i nexusbudget.p12 -o signing-key.txt
```

Paste the contents of `signing-key.txt` into the `SIGNING_KEY` secret.

- The default alias and password are both `nexusbudget`. The keystore file itself is the secret. To use a keystore with its own password, also add a `SIGNING_KEY_PASSWORD` secret.
- Keep a private backup of the keystore. If it's lost, a new key works for new installs, but existing installs have to be uninstalled before they can update.
- Anyone who has the keystore can sign apps that install over yours, so never commit it or share it.

## Forks

Forks publish to their own Releases page, and the in-app **Check for updates** button looks at the fork's repository automatically. Add your own `SIGNING_KEY` secret. The application ID `io.github.booned976.nexusbudget` in `app/build.gradle.kts` can be changed so a fork installs alongside the original.
