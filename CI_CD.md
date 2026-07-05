# CI/CD

Automated build checks and releases via GitHub Actions.

## Workflows

| Workflow | File | Trigger | What it does |
|---|---|---|---|
| **Android CI** | `.github/workflows/android-ci.yml` | every push & PR | builds the debug APK + lint; uploads the APK as a build artifact |
| **Release** | `.github/workflows/release.yml` | pushing a `v*` tag | builds a **signed** APK + AAB and attaches them to a GitHub Release |

## Cutting a release

```bash
# bump versionCode / versionName in app/build.gradle.kts first, then:
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow builds signed artifacts and publishes a GitHub Release named
after the tag, with `ScreenMirror-v1.0.0.apk` and `.aab` attached and
auto-generated release notes. This gives you an immutable, versioned, downloadable
history of every shipped build.

## Required repository secrets (for signed releases)

Set these under **Settings → Secrets and variables → Actions** (or via `gh secret set`):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | base64 of `upload-keystore.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (e.g. `upload`) |
| `KEY_PASSWORD` | key password |

Generate the base64 of the keystore:

```bash
base64 -w0 upload-keystore.jks         # Linux
# macOS:  base64 -i upload-keystore.jks
# Windows PowerShell:
#   [Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks"))
```

The keystore and passwords are **never** committed — the workflow reconstructs
them from secrets at build time and deletes them afterwards. The CI build check
needs no secrets (it builds the debug variant).

> Security: keep `upload-keystore.jks` backed up offline. If it leaks, rotate it —
> for Play, enrol in **Play App Signing** so Google holds the app signing key and
> the uploaded key is only an upload key.
