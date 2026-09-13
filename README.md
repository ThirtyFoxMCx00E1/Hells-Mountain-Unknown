# Hells Mountain Unknown — Incident 304

This build uses a **safe Android launcher → native game** split.

## Native ABIs

Checked-in native libraries are under:

- `app/src/main/jnilibs/armeabi-v7a/`
- `app/src/main/jnilibs/arm64-v8a/`
- `app/src/main/jnilibs/x86/`
- `app/src/main/jnilibs/x86_64/`

Each ABI contains `libunsolvedcase.so` and the matching `libc++_shared.so`.

`app/build.gradle` explicitly maps `src/main/jnilibs` as the JNI library directory.

## Startup/crash prevention

`MainActivity` never loads the native renderer. It shows the black frame and intro first.

The game activity is a normal Android `Activity` with a `SurfaceView`. Native rendering starts only after Android provides a valid `Surface`. This removes the previous `GameActivity`/`android_main` startup path that was failing during native initialization.

The renderer also explicitly binds `EGL_OPENGL_ES_API` before creating its EGL context.

The PojavLauncher project was used as a reference for the general idea of keeping Java-side startup separate from native runtime startup. The entire PojavLauncher application is **not** copied into this project because it is a different launcher application with unrelated Java/native components.

## Native source

The normal source is in `app/src/main/cpp/`.

An alternative ndk-build entry point is also provided in:

`app/src/main/jni/Android.mk`

with:

`app/src/main/jni/Application.mk`

It targets all four requested ABIs.

## Assets

The menu artwork remains in:

- `app/src/main/assets/menu/bg_main.png`
- `app/src/main/assets/menu/logo_main.png`

The Java menu loads the logo during startup and defers the larger background until after the logo intro.

## Building

Run:

```text
./gradlew assembleDebug
```

The APK will package the checked-in native libraries.

For a clean native rebuild with Android NDK, use the `app/src/main/jni/Android.mk` recipe and copy the resulting `libunsolvedcase.so` files back into `app/src/main/jnilibs/<ABI>/`.



## 0.3.1 stability update

ReLinker 1.4.5 + xCrash 3.1.0, four ABIs, delayed native loading, three graphics profiles, and regenerated menu textures. xCrash records Java/native/ANR tombstones in the app-private files/tombstones directory. See GRAPHICS_AND_CRASH_STACK.md.


## Permissions

This build declares exactly two Android permissions: `POST_NOTIFICATIONS` and `VIBRATE`. The notification permission is requested after the launcher first renders, and permission failure never blocks startup.


## App identity

- Display name: **Hells Mountain Unknown**
- Launcher icon: the updated eye artwork supplied for this release.
- The existing Java package/application ID and native library name are intentionally unchanged because they are technical identifiers used by the JNI/native startup path.
