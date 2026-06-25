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
