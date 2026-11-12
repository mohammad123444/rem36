# Debug and the first release build keep minify off.

# sherpa-onnx: the Kotlin classes are called from native code.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
