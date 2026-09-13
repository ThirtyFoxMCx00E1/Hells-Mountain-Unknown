# 0.3.2 native build fix

The previous project contained prebuilt `src/main/jnilibs/*/libunsolvedcase.so` files, but Gradle did not invoke the CMake project in `src/main/cpp`. That could leave the APK running an older native binary even after C++ sources were edited.

This version makes CMake the authoritative native build, includes `audio_engine.cpp`, and explicitly registers the three JNI methods from `JNI_OnLoad`. ReLinker remains in the Java loader and xCrash remains available for diagnostics.
