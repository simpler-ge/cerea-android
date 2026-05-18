# Cerea Android SDK

Embed a Cerea General Chat agent in your Android app.

## Install

Add JitPack to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.simpler-ge:cerea-android:0.1.0")
}
```

## Use

```kotlin
import com.cerea.chat.CereaChatFragment

val chat = CereaChatFragment.newInstance(
    token = "your-widget-token",
    attributes = mapOf(
        "user_id" to "user-42",
        "name"    to "Luka",
        "plan"    to "pro"
    )
)
supportFragmentManager
    .beginTransaction()
    .replace(R.id.container, chat)
    .addToBackStack(null)
    .commit()

// Dynamic context updates as the user navigates
chat.updateContext(mapOf("current_screen" to "billing"))
```

## Required permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If you allow visitors to attach media, also add:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## Configure the agent

1. In the Cerea dashboard, go to **Agents → General Chat → Create Agent**.
2. Pick **Android SDK** as the surface.
3. Add your app's package name (e.g. `com.acme.app`) to the allowlist.
4. Copy the widget token and paste it into the SDK call.
