# SURAS Android release rules.
#
# The UniFFI bindings and JNA use JNI and reflection, SQLCipher is reached
# through native methods, and ONNX Runtime uses JNI. Keep those entry points.

-keep class com.harisudhan.suras.core.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class ai.onnxruntime.** { *; }

-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn org.slf4j.**
-dontwarn com.sun.jna.**
