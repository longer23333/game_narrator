# ONNX Runtime crosses JNI boundaries and its Android artifact supplies the
# implementation. Preserve native method names and the public runtime surface.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
