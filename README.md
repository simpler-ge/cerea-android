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
    implementation("com.github.simpler-ge:cerea-android:0.1.4")
}
```

`minSdk` ≥ 24 is required.

> **WebView requirement.** The chat widget is loaded into a `WebView` and
> uses dynamic `import()`, which needs **Android System WebView 63 or
> newer**. `minSdk` alone is not sufficient: a stock Android 8.0 device
> ships WebView 58 and the widget will fail to start (`SyntaxError:
> Unexpected token import`) until the user updates *Android System
> WebView* from the Play Store. Devices with Play Services generally
> receive this update automatically; devices without it do not.

## Use

```kotlin
import com.cerea.chat.CereaChatFragment

val chat = CereaChatFragment.newInstance(
    token = "<widget-token-from-dashboard>",
    userToken = userTokenFromYourBackend,        // identity + history (see below)
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

### Closing the chat

The widget's header shows a back arrow. Tapping it does exactly what the
system back does — pops the chat off the back stack, finishes an activity that
holds only the chat, or runs any back callback you registered — and then calls
`onClose`:

```kotlin
chat.onClose = { Log.d("Chat", "chat closed") }   // optional
```

### Widget events (0.1.4+)

```kotlin
chat.onEvent = { event ->
    when (event) {
        CereaChatEvent.READY -> {}   // widget loaded its configuration
        CereaChatEvent.OPEN -> {}    // chat window showing
        CereaChatEvent.CLOSE -> {}   // user tapped the header back arrow
    }
}
```

You do not need to act on `CLOSE`; the fragment goes back itself right after
reporting it. Events arrive on the main thread. Like any fragment callback,
`onEvent` and `onClose` are not kept across configuration changes — set them
again on the restored instance.

> If you previously reached into the fragment's `WebView` to listen for these
> events, remove that code when you update. An interface of your own named
> `CereaAndroid` replaces the SDK's, and the header back arrow stops working.

### Identity & history

If `userToken` is provided — an HS256 JWT signed by your backend with
the agent's HMAC secret (claims: `aud: "cerea-identity"`, `user_id`,
`exp` ≤24h) — the visitor's conversations persist across devices and
reinstalls, and the in-widget history drawer activates.

**A visitor identity is required to start a conversation, and on Android
`userToken` is the only way to supply one.** The session endpoint rejects
anonymous visitors with `identity_required`.

The pre-chat form is **not** an alternative here: the widget only renders
it on the `webchat` surface, so enabling it on a mobile agent has no
effect no matter what the dashboard shows.

Without a `userToken` the widget still renders and shows the greeting —
so it looks like it is working — but no session exists, sending a message
does nothing, and attaching a file fails with `401`.

The JWT must be signed with the agent's **HMAC secret exactly as shown in
the dashboard** (the hex string is used as UTF-8 text, not decoded to
bytes) and must include an `iat` claim — tokens without `iat` are
rejected with `invalid_user_token`. Use `user_id`; `sub` alone is not
accepted.

```
header  { "alg": "HS256", "typ": "JWT" }
payload { "aud": "cerea-identity", "user_id": "<your id>",
          "iat": <now>, "exp": <now + ≤86400> }
```

### Self-hosted widget

```kotlin
CereaChatFragment.newInstance(
    token = "...",
    host = "https://chat.example.com",   // your hosted widget URL
)
```

## Host activity configuration

The chat composer sits at the bottom of the fragment. Set the hosting
activity's soft-input mode so the keyboard resizes the view instead of
covering the composer:

```xml
<activity
    android:name=".ChatActivity"
    android:windowSoftInputMode="adjustResize" />
```

## Required permissions

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<!-- Only if you allow file/media attachments: -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## Attachments

The fragment implements `WebChromeClient.onShowFileChooser` and opens the
system picker itself, so the widget's attachment button works with no extra
integration. (Android WebView has no built-in picker — without that override
`<input type="file">` is silently inert.)

## Configure the agent

1. In the Cerea dashboard, go to **Agents → General Chat → Create Agent**.
2. Pick **Android SDK** as the surface.
3. Add your app's package name (e.g. `com.acme.app`) to the allowlist.
4. Copy the widget token and paste it into the SDK call above.

## Security notes

The SDK locks down the WebView (no file/content URL access, camera/mic
permission gated on the configured host) and routes external links to the
system browser. Its one JavaScript interface, `CereaAndroid`, exposes a
single `postMessage(String)` and acts only while the page showing is the
configured widget host. JS injection
sites are escaped via `JSONObject` plus U+2028 / U+2029 hardening.

## License

MIT — see `LICENSE`.
