# BNEL Dicom Cleaner 1.1.0

Portable Windows x64 app for bulk metadata cleaning, optional image inspection, and folder/ZIP/DICOMDIR export of CT, MRI and registration DICOMs.

## Start

Extract the entire ZIP to a writable folder and use its new **BNEL Dicom Cleaner 1.1.0** folder. Do not overwrite an earlier version in place; the new path also avoids a stale Explorer icon cache. Open **BNEL Dicom Cleaner.exe**. Keep `app` and `runtime` alongside it. Java, codecs, PDF preview and the UI libraries are included; no installation, terminal or internet connection is needed.

To carry over preferences, close the old app and copy its `BNEL-settings.properties` next to the new executable. The obsolete profile setting is ignored. Patient replacement fields reset when another import is added. This unsigned private review build may receive the normal Windows downloaded-app prompt.

## Clean and export

1. Import one or more folders (Ctrl/Shift selection is supported), add files, or drag folders into the app. The complete imported set is selected initially. The source tree also supports multiple selections.
2. Enter either replacement Patient Name or Patient ID. **Linked** mirrors edits in either direction; turn it off when different values are needed. One pair applies to the current selection, including when it spans multiple folders. For different replacement identities, clean/export each relevant selection separately.
3. Choose a fixed synthetic date or **Random per folder**. Random dates are selected in 2000–2099 and remain consistent for each import root during this session. Fixed mode uses the entered date. Original date intervals are not retained; time values are zeroed or emptied.
4. Adjust **Identifier rules**, optional **Essential research metadata**, and **Scan labels** as needed, then **Clean selected**.
5. Export as soon as metadata cleaning completes. Image review is optional; no approval step is required. Unsupported/invalid files are skipped; the result window lists them and they are excluded from export. Valid files continue processing.

Changing cleaning options, replacement fields, labels, custom rules or the imported set invalidates the staged output. Clean again to apply those changes. Original files are not overwritten by cleaning or export.

## Metadata inspection and rules

**Instance comparison** shows every parsed original/cleaned tag of the selected file. Double-click a value for the full text or binary inspection. Right-click a tag and choose **Always remove this tag** to save a rule immediately. **Identifier rules → Custom tags** also accepts `(0018,1030)` or `00181030`; remove a saved rule there to stop applying it. Rules act recursively at every sequence depth in future cleaning jobs. Pixel data, required geometry and UID relationships are protected from arbitrary removal; required empty fields remain empty instead of disappearing.

**All metadata** is the first and default tab. Its equal-width **All original metadata** and **All cleaned metadata** panes remain side by side, including in portrait windows. They index the union of tags across every file, including tags present only in late-added files. A shared search filters both panes. Select a tag to see each file's value or an explicit missing entry; **Link selected tag** selects the matching tag on the other side. Dragging a horizontal divider aligns the per-file detail areas. **Varying / missing tags** filters variation between files within each pane. **Instance comparison** is the second tab. **Compare files** provides another view of differences.

The base metadata cleaner removes known identifying fields, private/unknown attributes, overlays, signatures, history, unneeded opaque payloads and unrestricted text, including long mixed text, recursively. Necessary technical coded values are retained. **Series Description is deliberately retained exactly from each source instance by default.** Protocol Name and Sequence Name default to blank. Use **Scan labels** for manual per-series overrides, or a custom removal rule to remove these tags. A removal rule takes precedence over a label override.

**Essential research metadata** starts checked for new settings; an explicitly saved preference is preserved. It additionally drops optional metadata outside an explicit classic CT/MR/registration allowlist. It preserves pixels, geometry, registration matrices, scan options, MR acquisition type, key acquisition/timing parameters such as TR/TE/TI, and chosen scan labels. Examples of additional removals include Acquisition Duration (0018,9073), Exposure in µAs (0018,1153) and Detector Temperature (0018,7001). The checkbox's tooltip explains the difference. Enhanced and secondary-capture objects automatically use the broader cleaner to retain their required structures.

Identifier category actions are Remove, Default (`REDACTED`), Random (consistent replacements per cleaning job), or Custom. They cover other names, physicians/operators, IDs/accession numbers, contact details, institutions/locations, manufacturer/device and trial fields. They apply to eligible text values; dates and UIDs have typed handling. Numeric/coded patient characteristics such as smoking status are removed/emptied. The app does not require exception notes or enforce a HIPAA workflow.

ImplementationVersionName, SourceApplicationEntityTitle and DeidentificationMethod are omitted from cleaned instance files. In the optional DICOMDIR, an existing optional writer label becomes `REDACTED` in place, preserving its absolute record offsets. The required ImplementationClassUID remains. No automatic pixel-review or facial-anonymity assertion is added.

## Interface and window controls

The 1.1 interface uses opaque neutral surfaces, a muted sage accent, regular Segoe UI at 13 logical pixels on Windows, and restrained semibold headings. Sources, metadata and controls share the same scale-aware typography. Tables retain complete values through selection and Full value actions; shortened paths and file labels retain their complete contents in hover help. Both aggregate panes start at equal widths, with aligned detail sections at a 75:25 split. The source sidebar starts at 25.5% of workspace width. User divider ratios survive window resizing, tab switches and relaunches. Reset layout is in the main command strip's context menu.

Settings occupy two aligned rows below the workspace. The fixed command strip beneath them groups Import/Add/Remove/Review on the left and Clean/Export/Clear on the right. Narrow windows move long settings groups into accessible bottom popovers; the identity fields stay visible. The exact author credit remains at the bottom right, using a second footer row when needed. Cancel appears only during supported running operations.

Both windows use native operating-system title bars and resize edges. Double-click empty title-bar space to maximize or restore. The viewer temporarily disables the main session while open. No extra corner grips or duplicate in-content window headings are drawn. The desktop UI remains Java/Swing with FlatLaf; this is not a WinUI rewrite.

## Optional viewer

The main workspace and viewer share a **Sources → subject → study → series → file** tree. Series folders use the source Series Description; hover over a source, series or file for its original path. Separate import roots remain separate even when labels or UIDs match. Click a branch to select its files, or use Ctrl/Shift for multiple branches/files. Mark/exclusion counts stay visible on collapsed groups. **Collapse** shows series folders and **Expand** reveals all files. Slice playback and the stack slider follow this same tree order.

- Drag the **Stack** slider to any slice across files and multi-frame instances. Wheel or Left/Right over the image advances slices; Page Up/Down changes files. When the Sources tree has focus, Left/Right collapses/expands branches. The old image remains visible while the next one decodes. A bounded cache preloads nearby slices and rapid scrubbing prioritizes the latest requested position.
- **Play / Pause** (Space) advances the stack at the chosen 1–60 fps target, stopping at the end. Actual speed depends on disk, compression and image size. Playback waits for decoding rather than skipping unrendered images.
- Adjust width/level, zoom, Ctrl+wheel zoom or **Auto / Fit**. Window settings stay stable within a series. Marking/exclusion applies to whole files, including all frames/pages.
- **Mark file** or M adds a visible amber **[MARKED]** row. **Select all unmarked** selects all unmarked, unexcluded files. **Mark all selected** and **Unmark selected** affect the entire selection and keep it intact. Already-marked or unmarked files are accepted without errors. Approval controls have been removed; exporting never requires approval.
- **Exclude marked from export** removes those files from the session when the viewer closes. With no marks it excludes the displayed file. **Undo exclusion** is available before closing. Excluding a file from a cleaned set requires cleaning the remaining selection again to rebuild references. Originals remain intact.
- **Move originals to Recycle Bin** is a separate, confirmed operation listing exact source files. It never falls back to permanent deletion. It checks source hashes and refuses directories, network/removable storage and unsupported recycling. Source DICOMDIR indexes are not rewritten. Restore through Windows Recycle Bin and reimport if needed.
- Embedded PDF reports (up to 64 MB) render inside the app; PDF scripts/links/attachments are not run. Report/text and full metadata remain accessible. PDFs, structured reports and other unsupported SOP classes are inspection-only and skipped during cleaning.
- On cleaned monochrome images, draw rectangles and apply a blackout to every frame of the current staged instance. Source pixels are unchanged. This is manual 2D redaction, not 3D defacing.

Source search filters the tree view only. It retains ancestors and preserves the operation selection, including hidden selected files, until you explicitly change that selection. Clearing the search restores the tree. Single-clicking the selectable width of a file row opens that file; preview navigation uses the updated selection model's lead, not a previous display row. Tree clicks pause playback. Manual tree scrolling suspends playback-follow scrolling, while explicit transport or stack navigation resumes it. Marking and other image-dependent actions are disabled while a different slice is loading. Queued pixel decoding remains bounded and latest-request-wins.

## Export and preferences

Directory nesting is preserved by default using neutral folder/file names. Enable **Retain original folder/file names** to keep the exact imported relative names. Multiple import roots get separate output root directories. **Include DICOMDIR** is optional and requires no more than eight path components, each 1–8 uppercase letters/digits/underscores. Use neutral names or disable preserved hierarchy if the imported paths do not fit that format. ZIP contains the same exported tree.

Export to a new folder/ZIP outside the imported source roots. Existing output is never overwritten. A canceled or failed export does not leave a final destination. No compliance or review report is generated. UID remapping is consistent across the cleaning job. Incomplete registration objects are skipped while supported images continue; external image references are reported without an acknowledgment requirement. The results list the original referenced SOP Instance UID, every referring source path and tag/sequence path, and the target path when that file was imported. A target not loaded into the current session is explicitly described as having an unknown filename/path. This means an exported image links to a separate DICOM instance outside this export; it does not mean the referring image was skipped. The result window is resizable and its details can be copied. Pixel bytes, geometry and source hashes are checked during cleaning/export.

Options, identifier category/custom tag rules, fixed date and window layout are remembered next to the executable in `BNEL-settings.properties`. Patient Name/ID, source paths, random-date mappings, original UID mappings and approvals are not persisted. Custom replacement text is persisted as entered.

Temporary staged data is deleted on normal session close/clear. A crash can leave `bnel-cleaner-*` folders in the operating system's temporary folder. Cleanup is ordinary deletion, not secure erasure.

## Scope and validation

Supported cleaning includes classic/enhanced CT/MR, selected secondary capture, and spatial/deformable registration objects. Unsupported formats and decoding/validation failures are skipped individually. Compressed JPEG/JPEG-LS metadata is checked; JPEG 2000 and other uninspected transfer syntaxes may require external DICOM decompression. Color blackout is not implemented. Preserved labels, filenames and unchanged image pixels can retain identifiers according to your choices. Automatic OCR and 3D defacing are not included in this revision; defacing work is paused for this review.

The protected cleaning, deidentification, UID/date, pixel safety, residual checking, export, recycling and decoder implementations are unchanged from the accepted 1.0.0 release. Validation uses synthetic fixtures only. See VALIDATION.md in the private maintenance checkpoint for the exact checks and environment limitations.

The Windows ZIP contains the app executable, its application libraries, complete bundled Windows Java runtime, native recycling adapter and required license notices. It intentionally omits source, test fixtures, screenshots, build tools, development logs and extra readme files. Extract the entire ZIP; keep app and runtime beside the executable. The new icon is embedded into the EXE at seven resolutions and in the app window.

The private maintenance checkpoint retains this source separately from the runtime ZIP. No revision was published to the public GitHub fork. Build dependencies remain PixelMed 20260916, its bundled codecs/vecmath, PDFBox 3.0.8, FlatLaf 3.7 and Temurin Windows Java 21.0.12.1+1. The accepted release's six dependency JARs can be used as a checksum-locked cache via vendor-lock.json when upstream current archives rotate. Run build.py on a development machine with JDK 17+; --gui-test requires a graphical display. private_repackage.py reuses and verifies the accepted Windows runtime and launcher, replacing icon/version resources without changing executable code. The supplied icon encoder uses Pillow and the packager uses LIEF on the build machine only.

Boney Joseph, M.B.B.S. | Bioelectronics Neurophysiology and Engineering Lab
