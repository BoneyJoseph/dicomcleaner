#!/usr/bin/env python3
"""Reproducible source build. Network is used at build time only; the packaged app is offline."""
from pathlib import Path
import argparse, hashlib, json, os, platform, shutil, subprocess, tarfile, urllib.request, zipfile

ROOT = Path(__file__).resolve().parent
BUILD, VENDOR = ROOT / 'build', ROOT / 'vendor'
VERSION = '0.1.0'
ARCHIVES = {
    'binaryrelease': '3b6c05abcf271aea226790f34655cc4091f1fa6943dda2ed225baadcaf748f74',
    'dependencyrelease': 'c2b45f23758d2ef8663aba6f03cec2ea5635ee498381fb700b53ccc4e1c0a51a',
}
JARS = ('pixelmed.jar', 'pixelmed_imageio.jar', 'pixelmed_codec.jar', 'vecmath1.2-1.14.jar')

def run(args):
    subprocess.run([str(x) for x in args], cwd=ROOT, check=True)

def java_tool(name):
    suffix = '.exe' if os.name == 'nt' else ''
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + suffix) if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if found:
        return [found]
    if name == 'javac':
        return [*java_tool('java'), '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    if name == 'jar':
        return [*java_tool('java'), '-m', 'jdk.jartool/sun.tools.jar.Main']
    raise RuntimeError(f'{name} requires a JDK 17 or newer on the build machine.')

def dependencies():
    cache = BUILD / 'downloads'; cache.mkdir(parents=True, exist_ok=True)
    VENDOR.mkdir(exist_ok=True)
    notices = BUILD / 'notices'; notices.mkdir(exist_ok=True)
    for kind, expected in ARCHIVES.items():
        filename = f'pixelmedjavadicom_{kind}.20260916.tar.bz2'
        archive = cache / filename
        if not archive.exists():
            url = f'https://www.dclunie.com/pixelmed/software/current/{filename}'
            with urllib.request.urlopen(url, timeout=90) as response, archive.open('wb') as out:
                shutil.copyfileobj(response, out)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            archive.unlink(missing_ok=True)
            raise RuntimeError('Dependency checksum mismatch; build stopped.')
        with tarfile.open(archive, 'r:bz2') as tar:
            for member in tar:
                # Extract selected bytes only. No archive paths, devices, or symlinks are trusted.
                name = Path(member.name).name
                if member.isfile() and (name in JARS or name in ('COPYRIGHT', 'README')):
                    if name in JARS:
                        target = VENDOR / name
                    elif name == 'COPYRIGHT':
                        target = notices / f'PixelMed-{kind}-COPYRIGHT.txt'
                    else:
                        target = notices / f'PixelMed-{kind}-README.txt'
                    target.write_bytes(tar.extractfile(member).read())
    for name in JARS:
        if not (VENDOR / name).is_file():
            raise RuntimeError(f'Missing dependency {name}')
        with zipfile.ZipFile(VENDOR / name) as z:
            for entry in ('COPYRIGHT', 'README'):
                if entry in z.namelist():
                    (notices / f'{name}-{entry}.txt').write_bytes(z.read(entry))
    (notices / 'dependency-sha256.json').write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in VENDOR.glob('*.jar')}, indent=2))
    return notices

def build(test=True, gui=False, package=False):
    notices = dependencies()
    classes = BUILD / 'classes'
    shutil.rmtree(classes, ignore_errors=True); classes.mkdir(parents=True)
    cp = os.pathsep.join(str(VENDOR / x) for x in JARS)
    sources = sorted((ROOT / 'src').rglob('*.java'))
    run([*java_tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp, '-d', classes, *sources])
    for resource in (ROOT / 'resources').iterdir():
        if resource.is_file():
            shutil.copy2(resource, classes / resource.name)
    test_classes = BUILD / 'test-classes'
    if test or gui:
        shutil.rmtree(test_classes, ignore_errors=True); test_classes.mkdir(parents=True)
        app_cp = str(classes) + os.pathsep + cp
        run([*java_tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', app_cp, '-d', test_classes, *sorted((ROOT / 'test').rglob('*.java'))])
        test_cp = str(test_classes) + os.pathsep + app_cp
        if test:
            run([*java_tool('java'), '-Djava.awt.headless=true', '-cp', test_cp, 'org.bnel.dicom.EngineTest'])
        if gui:
            run([*java_tool('java'), '-cp', test_cp, 'org.bnel.dicom.GuiTest', BUILD / 'screenshots'])
    app = BUILD / 'app'
    shutil.rmtree(app, ignore_errors=True); app.mkdir()
    manifest = BUILD / 'MANIFEST.MF'
    manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.bnel.dicom.Main\nClass-Path: ' + ' '.join(JARS) + '\n\n', encoding='utf-8')
    run([*java_tool('jar'), '--create', '--file', app / 'BNEL-Dicom-Cleaner.jar', '--manifest', manifest, '-C', classes, '.'])
    for name in JARS:
        shutil.copy2(VENDOR / name, app / name)
    if package:
        if platform.system() != 'Windows':
            raise RuntimeError('Windows packages must be built on Windows. Use the supplied GitHub Actions workflow.')
        dist = BUILD / 'dist'; shutil.rmtree(dist, ignore_errors=True)
        run([*java_tool('jpackage'), '--type', 'app-image', '--name', 'BNEL Dicom Cleaner',
             '--input', app, '--dest', dist, '--main-jar', 'BNEL-Dicom-Cleaner.jar', '--main-class', 'org.bnel.dicom.Main',
             '--app-version', VERSION, '--vendor', 'Bioelectronics Neurophysiology and Engineering Lab',
             '--description', 'BNEL Dicom Cleaner', '--copyright', 'BNEL; includes PixelMed and other licensed components',
             '--add-modules', 'java.base,java.desktop,java.logging,java.xml,java.prefs,java.naming,java.sql,jdk.charsets,jdk.unsupported',
             '--java-options', '-XX:MaxRAMPercentage=65'])
        # No --win-console: launcher is a GUI subsystem executable.
        portable = dist / 'BNEL Dicom Cleaner'
        shutil.copy2(ROOT / 'README.md', portable / 'READ-ME.md')
        shutil.copytree(notices, portable / 'THIRD-PARTY-NOTICES')
        shutil.copy2(ROOT / 'LICENSE', portable / 'LICENSE.txt')
        archive = shutil.make_archive(str(dist / f'BNEL-Dicom-Cleaner-{VERSION}-Windows-x64'), 'zip', dist, portable.name)
        digest = hashlib.sha256(Path(archive).read_bytes()).hexdigest()
        Path(archive + '.sha256').write_text(digest + '  ' + Path(archive).name + '\n')
        print('Created', archive)
    else:
        print('Built', app / 'BNEL-Dicom-Cleaner.jar')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--gui-test', action='store_true')
    parser.add_argument('--package', action='store_true')
    args = parser.parse_args()
    build(gui=args.gui_test, package=args.package)
