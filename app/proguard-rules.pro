# Keep native method names for JNI resolution.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Native crash/loading libraries
