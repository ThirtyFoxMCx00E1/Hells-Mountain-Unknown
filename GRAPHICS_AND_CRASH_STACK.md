# Hells Mountain Unknown 0.3.1 stability/graphics stack

- ReLinker 1.4.5 loads c++_shared and unsolvedcase recursively.
- xCrash 3.1.0 records Java, native and ANR tombstones under app-private files/tombstones.
- Launcher stays Android-only until the user starts the game.
- Four native ABIs are packaged.
- Three graphics presets are available as JSON/ZIP assets: fast, balanced, high.
- Menu art was regenerated as lightweight RGB PNG assets.
- `src/main/jni/` is included as a legacy ndk-build path; Gradle's prebuilt `jnilibs` remain the primary APK path.
