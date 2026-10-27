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
JARS = ('pixelmed.jar', 'pixelmed_imageio.jar', 'pixelmed_codec.jar', 'vecmath1.2-1.14.jar', 'pdfbox-app-3.0.8.jar')

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
    pdf = VENDOR / 'pdfbox-app-3.0.8.jar'
    if not pdf.exists():
        with urllib.request.urlopen('https://downloads.apache.org/pdfbox/3.0.8/pdfbox-app-3.0.8.jar', timeout=90) as response, pdf.open('wb') as out:
            shutil.copyfileobj(response, out)
    if hashlib.sha512(pdf.read_bytes()).hexdigest() != '768847238f683568507bf73570a2b6fedcbe58b25c7b4f97fba536ba110b290fe96ba065aed58629d41fb94857d76bc1978c2f31d294b553c69f287f71ee9600':
        pdf.unlink(missing_ok=True)
        raise RuntimeError('PDFBox checksum mismatch; build stopped.')
    for name in JARS:
        if not (VENDOR / name).is_file():
            raise RuntimeError(f'Missing dependency {name}')
        with zipfile.ZipFile(VENDOR / name) as z:
            for entry in ('COPYRIGHT', 'README', 'META-INF/LICENSE', 'META-INF/NOTICE'):
                if entry in z.namelist():
                    (notices / f'{name}-{entry.replace(chr(47), chr(45))}.txt').write_bytes(z.read(entry))
    (notices / 'dependency-sha256.json').write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in VENDOR.glob('*.jar')}, indent=2))
    return notices

def native_recycle():
    if os.name != 'nt':
        return None
    native = BUILD / 'native'; native.mkdir(exist_ok=True)
    vswhere = Path(os.environ.get('ProgramFiles(x86)', 'C:/Program Files (x86)')) / 'Microsoft Visual Studio/Installer/vswhere.exe'
    installation = subprocess.check_output([str(vswhere), '-latest', '-products', '*', '-requires', 'Microsoft.VisualStudio.Component.VC.Tools.x86.x64', '-property', 'installationPath'], text=True).strip()
    if not installation:
        raise RuntimeError('Visual Studio C++ build tools are required on the build machine.')
    devcmd = Path(installation) / 'Common7/Tools/VsDevCmd.bat'
    jdk = Path(os.environ['JAVA_HOME'])
    command = subprocess.list2cmdline(['cl.exe', '/nologo', '/std:c++17', '/EHsc', '/MT', '/O2', '/LD', '/DUNICODE', '/D_UNICODE', '/I'+str(jdk/'include'), '/I'+str(jdk/'include/win32'), str(ROOT/'native/recycle.cpp'), '/link', '/OUT:'+str(native/'bnel-recycle.dll'), 'shell32.lib', 'ole32.lib', 'uuid.lib'])
    script = native / 'compile.cmd'
    script.write_text('@echo off\ncall "' + str(devcmd) + '" -arch=x64 -host_arch=x64 >nul\nif errorlevel 1 exit /b %errorlevel%\n' + command + '\n', encoding='utf-8')
    subprocess.run(['cmd.exe', '/d', '/c', str(script)], cwd=native, check=True)
    return native

def build(test=True, gui=False, package=False):
    notices = dependencies()
    native = native_recycle()
    native_option = ['-Dbnel.nativeDir='+str(native)] if native else []
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
            run([*java_tool('java'), *native_option, '-Djava.awt.headless=true', '-cp', test_cp, 'org.bnel.dicom.EngineTest'])
        if gui:
            run([*java_tool('java'), *native_option, '-cp', test_cp, 'org.bnel.dicom.GuiTest', BUILD / 'screenshots'])
    app = BUILD / 'app'
    shutil.rmtree(app, ignore_errors=True); app.mkdir()
    manifest = BUILD / 'MANIFEST.MF'
    manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.bnel.dicom.Main\nClass-Path: ' + ' '.join(JARS) + '\n\n', encoding='utf-8')
    run([*java_tool('jar'), '--create', '--file', app / 'BNEL-Dicom-Cleaner.jar', '--manifest', manifest, '-C', classes, '.'])
    for name in JARS:
        shutil.copy2(VENDOR / name, app / name)
    if native:
        shutil.copy2(native / 'bnel-recycle.dll', app / 'bnel-recycle.dll')
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
