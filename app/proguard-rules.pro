# 液态玻璃库：JitPack AAR 内缺少 native 库（jni/ 为空），
# 且其 NativeGauss / NativeChromaticAberration 在 object init 里直接 loadLibrary。
# 为保证任何路径都不会触发 UnsatisfiedLinkError，这里保留 native 符号不裁剪。
-keep class com.example.blur.NativeGauss { *; }
-keep class com.example.liquidglass.NativeChromaticAberration { *; }
-keep class com.example.liquidglass.NativeChromaticDispersion { *; }
-keep class com.example.liquidglass.LiquidGlassView { *; }
-keep class com.example.liquidglass.GlassLensRenderer { *; }
-keep class com.example.liquidglass.GlassMaterial { *; }

-dontwarn com.example.liquidglass.**
-dontwarn com.example.blur.**
-dontwarn org.jetbrains.annotations.**
