# Cerea Android SDK

Embed a Cerea chat agent inside your Android app — a full-screen
`Fragment` wrapping a `WebView` pointed at our hosted widget.

## Install

In your project-level **`settings.gradle.kts`**:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In your app module's **`build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.github.simpler-ge:cerea-android:0.1.1")
}
```

`minSdk` ≥ 24 is required.

## Use

```kotlin
import com.cerea.chat.CereaChatFragment

val chat = CereaChatFragment.newInstance(
    token = "<widget-token-from-dashboard>",
    userToken = userTokenFromYourBackend,        // optional, enables history
    attributes = mapOf("plan" to "pro"),
)
supportFragmentManager
    .beginTransaction()
    .replace(R.id.container, chat)
    .addToBackStack(null)
    .commit()

// Push context as the user navigates your app
chat.updateContext(mapOf("current_screen" to "billing"))
```

### Identity & history (optional)

If `userToken` is provided — an HS256 JWT signed by your backend with
the agent's HMAC secret (claims: `aud: "cerea-identity"`, `user_id`,
`exp` ≤24h) — the visitor's conversations persist across devices and
reinstalls, and the in-widget history drawer activates. Without it,
each install is anonymous and scoped to that device.

### Self-hosted widget

```kotlin
CereaChatFragment.newInstance(
    token = "...",
    host = "https://chat.example.com",   // your hosted widget URL
)
```

## Required permissions

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<!-- Only if you allow file/media attachments: -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## Configure the agent

1. In the Cerea dashboard, go to **Agents → General Chat → Create Agent**.
2. Pick **Android SDK** as the surface.
3. Add your app's package name (e.g. `com.acme.app`) to the allowlist.
4. Copy the widget token and paste it into the SDK call above.

## Security notes

The SDK locks down the WebView (no file/content URL access, no
`addJavascriptInterface`, camera/mic permission gated on the configured
host) and routes external links to the system browser. JS injection
sites are escaped via `JSONObject` plus U+2028 / U+2029 hardening.

## License

MIT — see `LICENSE`.
