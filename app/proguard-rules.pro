# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep Room entities
-keep class com.ck66.dusou.database.** { *; }

# Keep data classes used by Gson/JSON
-keepattributes Signature
-keepattributes *Annotation*

# ONNX Runtime (PaddleOCR)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# WCDB
-keep class com.tencent.wcdb.** { *; }

# PaddleOCR SDK
-keep class com.paddle.ocr.** { *; }
-dontwarn com.paddle.ocr.**

# Kotlin Metadata (required for reflection-based libraries)
-keep class kotlin.Metadata { *; }

# Room: keep generated DAO implementations
-keep class com.ck66.dusou.database.dao.** { *; }

# Commons Text
-keep class org.apache.commons.text.** { *; }
-dontwarn org.apache.commons.text.**
