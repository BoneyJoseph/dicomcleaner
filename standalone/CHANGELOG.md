# 1.1.0 — UI handoff implementation

- Neutral opaque surfaces, sage primary action, regular Segoe UI, scaled text/icons, aligned metadata panes and two-row settings.
- Fixed bottom command groups, a responsive footer with the complete author credit, native window edges, remembered divider ratios.
- Searchable source trees with full-row hit targets, stable file identity, clear counts and complete path hover help.
- Fixed stale lead-selection preview after tree clicks; manual tree browsing no longer triggers autoplay scroll jumps. Loading imagery cannot be edited under a different selection.
- Compact transport controls and complete frame/page counters; existing cleaning, pixel, export and disposal behavior preserved.
- Supplied icon embedded in the Windows executable and window; portable ZIP contains runtime files and license notices only.

# 1.0.0 — Windows 11 interface

- Replaced the tinted/gradient Halo Light surfaces with a consistent neutral Windows light palette, white content areas, dark text, and restrained blue action/selection accents.
- Use 14-pixel regular Segoe UI on Windows with semibold headings. Removed bold source-tree labels and kept native font rendering and display scaling.
- Unified scroll-area backgrounds, table stripes, borders and icons; eliminated mismatched surface patches. Rounded controls retain hover, pressed, focus and tooltip feedback.
- Main commands now sit under the metadata workspace, with Review images before Clean selected. Metadata/export options and identity/date fields form balanced groups. Cancel is shown only during active work, beside progress.
- The viewer is a native decorated, resizable frame with standard maximize/restore and title-bar double-click behavior. It retains main-session isolation and restores main-window interaction on close.
- Viewer playback and blackout tools flank the full-width stack slider; adjustment and exclusion controls are grouped below. Centered select/mark/unmark/close actions replace the removed approval controls.
- Landscape layouts target 2560×1440 and 3840×2160, plus smaller restored windows; portrait-specific switching is removed.
- Metadata cleaning, pixel handling, DICOMDIR/ZIP export, source-tree grouping, frame cache and file-disposal logic are unchanged from 0.4.0. Essential research metadata still defaults on and respects saved preferences.

Validation: 430 engine assertions and 67 headful GUI assertions; checks cover imports, side-by-side metadata, linked identities, native viewer lifecycle, bulk marks, reports, exclusions, smooth scrubbing/cine, 8,000-instance navigation and landscape resizing. See READ-ME.md for the Windows execution limitation. This revision does not add 3D refacing.

# 0.4.0 — Halo Light and source trees

- PassCraft-inspired white/mint/ice-blue chrome, teal accents, rounded controls and search fields, compact top toolbar, and neutral image canvas.
- One source/subject/study/series/file hierarchy for the main app and viewer. Series names come from the source Series Description; hover reveals original paths.
- Separate import folders remain separate; branch selections apply bulk marking/approval actions to all child files. Collapsed folders show mark/review/exclusion counts.
- Playback and scrubbing follow the displayed tree order. The image remains visible while the next slice decodes.
- Short hover help for action buttons, tree controls and viewer settings.
- Essential research metadata starts checked when no saved preference exists.
- Preserves the 0.3.0 DICOMDIR runtime fix, side-by-side default metadata comparison, linked replacement identity, bulk review actions, optional image review and private portable packaging.

# Version 0.3.0 — private review build

- Fixed DICOMDIR export packaging: included `java.rmi`, reproduced the original missing-class error, and tested folder/ZIP export using the required runtime module list. The review ZIP bundles the complete verified Windows JRE.
- Added Mark all selected, Unmark selected, Remove approval of selected, and Remove all approvals. Mixed/already-cleared selections are accepted; selection is retained. Marking clears an existing approval.
- Made All metadata the first/default tab, with Original and Cleaned panes side by side, one shared search, linked tag selection and aligned detail dividers. Instance comparison is second.
- Linked Patient Name and Patient ID edits in either direction, with an unlink option for different values. Replacement identity is not persisted and resets on import.
- Reworked controls to compact Windows App styling: neutral surfaces, restrained gradients, blue accents, Segoe UI on Windows, consistent spacing and scalable outline icons.
- Added missing-reference details: original target UID, every referring file and nested reference tag, known target paths, and an explicit unknown-path status for targets not loaded.
- Embedded the icon in the Windows executable at seven sizes using bitmap/PNG resources. A versioned extraction folder reduces stale Explorer icon caching.
- Kept image review optional, unsupported-file skipping, current scan-label defaults and silent feedback. Defacing remains paused.

Validation: 430 engine assertions and 43 headful GUI assertions including 3840×2160 landscape, 1440×2560 portrait and compact windows, both directions of linked identity editing, shared metadata search/selection, bulk marks and approval removal, an 80-slice cine test, and 8,000-entry navigation. See READ-ME.md for the exact Windows execution limitation.

# Version 0.2.0 — private review build

- Whole-stack scrubber, nearby-frame prefetch, bounded cache and no blanking between decoded slices.
- Play/Pause with speed control, stable windowing within a series, and keyboard navigation.
- Visible amber [MARKED] rows; select all unmarked and approve selected in bulk.
- Metadata export works without image review or approval. No profile/exception notes, compliance reports or compliance acknowledgment dialogs.
- Fixed synthetic date or random date per imported folder; date-shift mode removed.
- Persistent recursive custom tag-removal rules, including right-click from instance comparison.
- Source Series Description retained per file; Protocol/Sequence labels blank by default and manually editable.
- Optional writer/deidentification labels removed from cleaned files; DICOMDIR writer labels use REDACTED without changing directory offsets.
- Unsupported/invalid files and incomplete registration objects skipped with a result list; other supported images continue.
- Modern consistent controls, distinct tabs, slim scrollbars, responsive portrait layout, both lower resize grips and new app/launcher icon.
- Removed Settings, Exceptions, profile selection and extra Maximize/Restore buttons. All app feedback is silent.
- No 3D defacing integration in this revision.

Validation: 420 engine assertions plus headful GUI tests, an 80-slice synthetic cine/scrub test, and navigation of an 8,000-entry list. Windows runtime/launcher/native recycler are retained from the previously tested build; this revised UI has not yet been executed on Windows in this session. See READ-ME.md.
