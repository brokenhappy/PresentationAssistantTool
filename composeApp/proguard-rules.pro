-keepattributes *Annotation*

# Kotlin serialization
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.woutwerkman.pa.**$$serializer { *; }
-keepclassmembers class com.woutwerkman.pa.** {
    *** Companion;
}
-keepclasseswithmembers class com.woutwerkman.pa.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# BLE
-keep class android.bluetooth.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
