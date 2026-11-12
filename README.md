# BNEL Dicom Cleaner

Portable Windows app for reviewing and de-identifying CT, MR and spatial/deformable registration DICOM files. Research validation release — not a certified de-identification service or diagnostic viewer.

- **App source, full documentation and build instructions:** [`standalone/`](standalone/) — start with [`standalone/README.md`](standalone/README.md), [`CHANGELOG.md`](standalone/CHANGELOG.md) and [`VALIDATION.md`](standalone/VALIDATION.md).
- **UI design handoff:** [`ui-handoff/`](ui-handoff/)
- **Release validation logs and scripts:** [`validation/`](validation/)
- **1.1.0 icon-repair notes:** [`docs/RESTORE-1.1.0-icon-repair.md`](docs/RESTORE-1.1.0-icon-repair.md)

## Getting a Windows build (no Windows machine needed)

GitHub Actions builds the portable app on Windows: **Actions → BNEL Windows portable → Run workflow** (it also runs automatically on pushes to `master` that touch `standalone/`). Download the `BNEL-Dicom-Cleaner-Windows-x64` artifact, extract the whole zip on the Windows PC, and run `BNEL Dicom Cleaner.exe` with `app` and `runtime` beside it.

The build is offline: the six dependency jars are committed in `standalone/vendor/` and verified against `standalone/vendor-lock.json`.

## Provenance

This repository started as a fork of [codeminders/dicomcleaner](https://github.com/codeminders/dicomcleaner) (a 2018 PixelMed DicomCleaner variant). As of 1.1.0 it contains only the BNEL standalone app, built on the [PixelMed](https://www.dclunie.com/pixelmed/software/) toolkit; see `standalone/LICENSE` and `standalone/build/notices/` for third-party licenses. The pre-1.1.0 history is not kept on GitHub.

Boney Joseph, M.B.B.S. | Bioelectronics Neurophysiology and Engineering Lab
