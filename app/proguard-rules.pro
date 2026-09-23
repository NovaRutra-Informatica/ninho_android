# LiteRT-LM calls Kotlin/JNI entry points by name from its bundled native runtime.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
