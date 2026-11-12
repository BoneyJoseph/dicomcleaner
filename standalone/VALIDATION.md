# BNEL Dicom Cleaner 1.1.0 — validation and maintenance

Final runtime ZIP: BNEL-Dicom-Cleaner-1.1.0-Windows-x64.zip
SHA-256: c9ef902ca3a7f58ee25fc596557d5713e0bc08a4aa0dd592c897123709fe92b5
Size: 66,037,692 bytes. 337 files. Runtime files and license notices only.

## Scope and parity

The protected Cleaner, Policy, IdentifierPolicy, DicomIO, PixelSafety, ResidualGuard, FileDisposal, NativeRecycle, MetadataDifferences and ViewerFrames sources are byte-identical to the accepted 1.0.0 release. MetadataUnion.compute is also unchanged. No deidentification defaults, UID/date behavior, reference validation, pixel safety, export structure, DICOMDIR constraints or recycling confirmations were changed. No defacing was integrated.

Preserved workflows: import folders/files and drag/drop; explicit selection cleaning; linked/reset replacement name and ID; fixed/random synthetic dates; persistent identifier rules; custom recursive tag removal; scan-label overrides; complete per-file and aggregate metadata inspection; shared search and linked selected tags; optional image/PDF/report viewing; stack slider and cine; mark/unmark and exclusions; manual blackout; protected recycling; folder/ZIP/DICOMDIR export; settings persistence.

## Completed checks

- Baseline before changes: 430 engine assertions and 67 headful GUI assertions.
- Release build: 430 engine assertions, including synthetic CT/MR/REG, DICOMDIR readback and offsets, ZIP/folder structure, source immutability, retained geometry, unsupported files, reference details, custom rules, manual blackout and cancellation.
- Final GUI regression: 68 assertions, native title-bar double-click maximize/restore under the test window manager, image/PDF navigation, playback, 8,000-instance range/navigation, resizes and tooltips.
- Final handoff acceptance at 100% scale: 57 assertions. Full-width leaf clicks at beginning/middle/end and across series/filtering; correct lead selection; clicks pause playback; latest request wins; hidden selections survive search; no tree viewport jump during playback browsing; compact controls, paired geometry, full footer credit, saved divider ratios.
- Font/icon and layout scale checks: 125% (57 assertions), 150% (58), 200% (57). Final 200% check passed again after the frame/page counter and narrow transport layout update. These are FlatLaf scale simulations on Linux, not Windows DPI tests.
- The 3,597-instance synthetic tree setup took approximately 63–143 ms in the captured runs; last final runs were 77 ms and 80 ms. This measures model/window setup, not uncached decoding of real clinical images.
- Bounded 192 MB pixel cache and latest-request-wins decoder preserved.
- Runtime bytes, all six third-party JARs and native recycling adapter matched the accepted release. java.rmi is included for DICOMDIR.
- EXE remains Windows GUI subsystem (no console). Entry point, executable code sections and manifest match the prior launcher. Version is 1.1.0. Seven embedded icon resources (16/24/32/48/64/128/256) match the provided artwork's generated ICO bytes.
- Delivery ZIP integrity checked; no source, tests, screenshots, preferences, sample DICOMs or build tools are included.

## Environment limits

Compilation and tests ran on Linux OpenJDK 17 with Xvfb/Openbox. Windows execution, Windows font rasterization/DPI behavior, Snap Layouts and actual Windows Recycle Bin operations were not available in this session. The packaged runtime is the unchanged complete Temurin Windows x64 JRE 21.0.12.1+1 and native launcher/recycler from the previously accepted release. Do not represent simulated scaling or Linux native-frame testing as a Windows test.

## Private maintenance

This checkpoint is separate from the lean user delivery. It contains source, tests, artwork, locked vendor libraries, license notices and packaging/validation scripts. No changes were pushed to the public GitHub fork.

For a future repackage, use the accepted 1.0.0 full ZIP as base; it contains the runtime provenance and file hashes required by private_repackage.py. Its Library ID is libfile_f0be77b710888191b54538bd038c3f02; filename BNEL-Dicom-Cleaner-1.0.0-Windows-x64.zip; SHA-256 f77716307dfbee02820461da68a1bd89bf5ba2bbc3995446f270a056292ed345. The lean 1.1.0 ZIP deliberately omits those development records.

Run python standalone/build.py for checksum-locked compilation/engine verification. Use --gui-test with a graphical desktop. On a packaging machine with LIEF 0.17.6, run python standalone/private_repackage.py ACCEPTED_1_0_0_ZIP OUTPUT_DIRECTORY. Pillow is needed only to regenerate ICO encoding from artwork/Icon_reference.png. No Python dependency is needed by the packaged Windows app.
