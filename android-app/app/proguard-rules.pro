# Room generates implementations and schema bindings from these annotated types.
-keep @androidx.room.Database class *
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ONNX Runtime crosses JNI boundaries and its Android artifact supplies the
# implementation. Preserve native method names and the public runtime surface.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
