# BNEL Dicom Cleaner — final icon repair checkpoint (2026-11-05)

## Current deliverable
release/BNEL-Dicom-Cleaner-1.1.0-Icon-Fixed-Windows-x64.zip is the lean portable app. Extract the entire archive to a fresh folder and run BNEL Dicom Cleaner.exe with its original filename; app and runtime must remain beside it. The paired configuration is app/BNEL Dicom Cleaner.cfg. Renaming the EXE breaks its configuration lookup.

## What changed
The prior working 1.1.0 build was used as the sole package baseline. Only the EXE icon resources and the JAR's app-icon.png changed. Configuration, application classes, dependencies, runtime and native adapter are byte-for-byte the working version. No defacing or policy changes. No new app compilation or engine tests.

The user-selected tight curved-square artwork is standalone/artwork/Approved-icon.png, decoded from the valid 256-pixel image in their supplied ICO. The original ICO is also retained as Approved-icon-supplied.ico. It contains a 512-pixel bitmap incorrectly advertised as 256; that malformed extra frame is not used. The new app-icon.ico includes valid 16/24/32/48/64/128/256 images. The icon design is preserved and there is no added padding. encode_icon.py now chooses Approved-icon.png first.

## Reproduce the exact icon repack
Use Python 3 with the versions in requirements-icon-repair.txt, and run:

python3 standalone/repair_icon_package.py baseline/BNEL-Dicom-Cleaner-1.1.0-Windows-x64.zip standalone/artwork/Approved-icon-supplied.ico output.zip

This script preserves the original launcher sections and only rewrites its existing last resource section; it does not re-link, add resource sections, or change configuration. It verifies all non-icon resources and all other archive files. Use this repair script, not the superseded LIEF icon updater from earlier backups.

## Major rebuilds
The complete current Java source, tests, native source, resources, vendor JARs and build scripts are in standalone/. See its README and VALIDATION for the original functionality and test history. A development JDK 17+ is required; full native Windows packaging also requires JDK jpackage and Visual Studio C++ Build Tools. Those external developer installations are not part of this backup. The known working 1.1.0 package and original 1.0.0 package (including runtime provenance and hashes) are included under baseline/ for recovery. Pinned vendor JARs and notices avoid retired upstream download URLs.

Earlier validation files describe the 1.1.0 GUI build. The current icon repair was checked for PE structure, icon payloads, matching configuration and unchanged code/package bytes. It was NOT executed on Windows here. A Wine test environment could not initialize on this Linux host, so no Windows launch test is claimed. The exact cause of the user's previous startup error was not reproduced; they recalled a possible missing-configuration error. This package keeps the entire working directory structure and launcher configuration intact.

No patient data or preferences are included. No public GitHub publication. Defacing remains paused until this build is reviewed.
