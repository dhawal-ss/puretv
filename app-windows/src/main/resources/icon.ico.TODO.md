# App icon placeholder

`compose.desktop.application.nativeDistributions` (see `app-windows/build.gradle.kts`)
expects a Windows `.ico` at `src/main/resources/icon.ico` for the MSI/EXE
installer and taskbar icon.

No image-generation/rasterization tooling is available in this build
environment, so this is a placeholder note rather than a binary `.ico`.

To finish this locally:
1. Design (or export from the brand palette in `ui/theme/DesktopTheme.kt` —
   `PureTvDesktopColors.TwitchPurple` / `Background`) a square PNG, ideally
   1024×1024, featuring the PureTV mark.
2. Convert it to multi-resolution `.ico` (16/32/48/256 px) with a tool such as
   ImageMagick (`magick icon.png -define icon:auto-resize=256,48,32,16 icon.ico`)
   or an online converter.
3. Save the result as `app-windows/src/main/resources/icon.ico` and delete this
   placeholder file.
4. Confirm `nativeDistributions { windows { iconFile.set(project.file("src/main/resources/icon.ico")) } }`
   is wired in `build.gradle.kts` (add it if missing).

This mirrors the Android TV banner placeholder noted under `app-tv/`.
