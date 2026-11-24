# PcViewer ("Viewer")

Default image, PDF and text viewer for LineageOS for PCs (`lineage_pc_x86_64`), the counterpart of Googlebook's
Viewer. A normal (non-privileged) Kotlin + Jetpack Compose + Material 3 app, package `org.lineageos.pcviewer`.

| Part | Where | What |
|---|---|---|
| `PcViewer` | `src/`, `res/` | the Compose app (`android_app`, `product_specific`, `sdk_version: "current"`) |
| `PcViewerCore` | `core/` | the viewer's logic in plain Kotlin, tested on the host |
| `PcTextEditorCore` | `packages/apps/PcTextEditor/core` | text decoding (encoding and line breaks), shared with the text editor |

## What it does

- **Opens** `VIEW` and `QUICK_VIEW` of `image/*`, `application/pdf` and `text/plain`, and `EDIT` of `image/*`
  (`content` and `file` URIs). Each file gets its own window (`documentLaunchMode="intoExisting"`,
  `taskAffinity=""`); opening a file that is already shown brings its window forward. From the launcher it shows a
  welcome with "Open file" (multi-select picker; the picked files can be stepped through).
- **Images** (everything `ImageDecoder` reads: JPEG, PNG, WebP, GIF, BMP, HEIF/HEIC, AVIF, DNG, ICO, WBMP): fit on
  open, never enlarged past actual size; zoom with the bar buttons, Ctrl+Plus/Minus, the mouse wheel or a pinch,
  around the pointer; drag to pan, limited to the image edges; double click switches fit and actual size; Ctrl+0
  fits; the zoom percentage refits on click. Rotate left/right (Ctrl+Shift+R / Ctrl+R) and **Save** the rotated
  image in place when its format can be written back (PNG, JPEG, WebP), or **Save as** a copy through the picker.
  Images over 32 megapixels are decoded subsampled and drawn at their real size.
- **PDF** through `android.graphics.pdf.PdfRenderer`: pages one under another, rendered only while in view and
  within a pixel budget, zoom relative to the window width (buttons, Ctrl+wheel), page indicator `3 / 12`.
  Streams that can't be seeked are copied to the cache first. Password-protected PDFs show a message.
- **Text**: read-only, monospace, selectable, decoded with the editor's detection (BOM, UTF-16, UTF-8,
  Windows-1252). "Edit file" hands it to a text editor with `ACTION_EDIT`; files over 4 MiB offer only that.
- **Previous / next in the folder**: DocumentsUI's quick view sends the folder's other files as clip items and
  the shown one's position as `EXTRA_INDEX`; the round buttons at the window edges and the Left/Right keys step
  through them, skipping files the viewer can't show.
- **Top bar**: file name, zoom, rotate, Save, Edit file (text), Share, and a menu with "Open…" and "File info"
  (name, type, size, date modified, image dimensions or page count).
- Texts in English (`res/values`) and Brazilian Portuguese (`res/values-pt`).

Googlebook's Viewer was used only as a layout reference (welcome screen, top bar actions, round previous/next
buttons, page indicator position and size, details dialog fields). No code, images, fonts or icons were taken
from it; the launcher icon is our own vector and the UI icons come from the Material icons library in the tree.

## Tests

```sh
core/run_tests.sh                       # 46 JUnit tests of the core, with external/kotlinc and JUnit from prebuilts
python3 -m unittest discover -s tests   # UI strings: both languages, same placeholders, no em dash
tools/typecheck.sh                      # compiles core + PcTextEditorCore + Compose UI against the prebuilts and the public SDK
```

`tools/typecheck.sh` needs `packages/apps/PcTextEditor` in the tree and the coroutines jar of a previous build in
`out/soong/.intermediates`; it builds no APK. In Soong the core tests are `PcViewerCoreTests`.

## Build integration

- `device.mk`: `PRODUCT_PACKAGES += PcViewer PcTextEditor` (the viewer links `PcTextEditorCore`)
- local manifest: `<project path="packages/apps/PcViewer" name="android_packages_apps_PcViewer" remote="pc" revision="lineage-24.0" />`
- framework overlay: `config_preferredSystemImageEditor` =
  `org.lineageos.pcviewer/org.lineageos.pcviewer.MainActivity`. The share sheet (IntentResolver's
  `ImageEditorActionFactory`) resolves `ACTION_EDIT` of the shared image against that component and shows an
  Edit button; the viewer answers it with rotate and save.
- DocumentsUI overlay: `trusted_quick_viewer_package` = `org.lineageos.pcviewer`. Without it the file manager
  sends a plain `VIEW` with one file, and previous/next have nothing to step through.

## Not done yet

- Never built with Soong or run on a device: only the host tests and the host typecheck have run.
- Animated GIF/WebP show their first frame; SVG is shown as text (there is no SVG renderer in the platform).
- Rotation is the only edit (Googlebook also crops, resizes and annotates). Saving in place re-encodes the
  image and drops its EXIF data.
- PDF: no text search, selection, links or password entry; page sizes are read up front, which is slow for
  PDFs with thousands of pages.
- A file opened with a plain `VIEW` (not quick view) has no neighbours; the folder can't be listed from a single
  document URI without a tree grant.
- Audio, video, rename, move to trash and print.

## Credits

This app is part of LineageOS for PCs and builds on:

- [LineageOS](https://lineageos.org) ([GitHub](https://github.com/LineageOS)): the platform and build tree.
- [The Android Open Source Project](https://source.android.com): the framework (PdfRenderer, ImageDecoder,
  DocumentsUI's quick view contract), the Kotlin compiler and the Jetpack Compose and AndroidX prebuilts.

Thanks to everyone behind these projects. Files taken from them keep their original licenses and copyright notices.
