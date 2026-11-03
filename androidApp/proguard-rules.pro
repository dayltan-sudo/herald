# kotlinx.serialization keeps generated serializers via its bundled consumer rules.
# Ktor / OkHttp optional dependencies
-dontwarn org.slf4j.**

# Comments on a selection read every text in the selection container by this name (CommentSelection.android.kt).
-keepclassmembers class androidx.compose.foundation.text.selection.SelectionState {
    public java.util.List getSelectableTexts();
}

# WebRTC (GPT-Live voice calls) is reached from native code by name, and its AAR ships no rules of its own.
# Native code calls only what it marks @CalledByNative (the default rules keep native methods' names), so keep
# those members, their classes and the types in their signatures; R8 may shrink and rename the rest.
-keepclasseswithmembers,includedescriptorclasses class org.webrtc.** {
    @org.webrtc.CalledByNative <init>(...);
}
-keepclasseswithmembers,includedescriptorclasses class org.webrtc.** {
    @org.webrtc.CalledByNative <methods>;
}
# Nothing in 150.7871.01 uses the unchecked form; it's here for a WebRTC update that does.
-keepclasseswithmembers,includedescriptorclasses class org.webrtc.** {
    @org.webrtc.CalledByNativeUnchecked <methods>;
}
-dontwarn org.webrtc.**
# Its JNI glue too: JNI_OnLoad finds these by name, and renamed they abort the app as a call starts.
-keep class org.jni_zero.** { *; }
-dontwarn org.jni_zero.**

# Terminal page: sshlib registers its Ed25519 provider and key classes by name, and termlib's native code
# calls back into its classes by name, so R8 must leave both alone. Their optional dependencies are absent.
-keep class com.trilead.ssh2.** { *; }
-keep class org.connectbot.terminal.** { *; }
-dontwarn com.trilead.ssh2.**
-dontwarn org.connectbot.**
-dontwarn com.google.crypto.tink.**
