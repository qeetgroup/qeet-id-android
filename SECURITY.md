# 🔒 Security

Qeet ID for Android holds real secrets on a phone Qeet does not control: the keys behind sign-in codes, and a
credential that approves Qeet ID sign-ins. This page says how they are protected, and how to report a problem.

| | |
|:--|:--|
| 🔐 **Secrets** | Sealed with an AES-256-GCM key in the Android Keystore (in the phone's secure hardware where it has one). One sealed file per account secret; the device credential in its own. Never in UI state, logs or notifications |
| 💾 **Backups** | None. Nothing is copied into a cloud backup or to a new phone; sign-in codes are moved by setting them up again |
| 🌐 **Network** | HTTPS only in release builds, to `api.id.qeet.in`. The device credential goes in request bodies, never in a URL or a bearer header; redirects are not followed |
| 👆 **Approving** | Needs the number shown on the sign-in screen and the owner's fingerprint, face or screen lock. A phone with no screen lock cannot approve |
| 🙈 **On screen** | Screens are kept out of screenshots, recordings and the recent-apps preview; other apps' overlays are hidden while the app is open; copied codes are marked sensitive and cleared after 30 seconds |
| 🔔 **Push** | Messages carry only a request id; the notification says nothing about the account. A phone that never links never registers with Firebase |
| 📷 **Camera** | Used only to read QR codes, decoded on the phone |

## 🛡️ Supply chain

- **Qeetrix for Android** is built from source at a pinned tag *and* commit (`gradle.properties`);
  `scripts/fetch-qeetrix.sh` refuses a clone whose commit differs.
- **Dependencies** are pinned in `gradle/libs.versions.toml`; the Gradle wrapper is pinned by checksum and validated
  in CI.
- **gitleaks** scans the history for secrets in CI. `google-services.json`, keystores and `keystore.properties` are
  git-ignored.

## 📅 Supported versions

| Version | Security fixes |
|:--|:--|
| `main` | ✅ Yes |

## 🚨 Reporting a vulnerability

**Don't report a vulnerability in an issue or a pull request:** anyone can read those.

1. Open the repository's **Security** tab and choose **Report a vulnerability**. Only the maintainers see the report.
2. Say what you found and its impact.
3. Explain how to reproduce it.
4. Name the version or commit you tested.

The maintainers reply in the private advisory, and publish it with the fix. Vulnerabilities in the Qeet ID service
itself belong to `qeet-id-server`'s own advisory.
