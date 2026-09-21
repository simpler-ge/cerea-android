# The widget calls CereaChatFragment's JavaScript interface by name; keep it
# through the consumer's minification.
-keepclassmembers class com.cerea.chat.CereaChatFragment$HostBridge {
    @android.webkit.JavascriptInterface <methods>;
}
