#!/usr/bin/env python3
"""Assemble a private review build with the existing Windows GUI launcher and a complete verified Windows JRE.
Run build.py first. No GitHub publication. Requires lief on the packaging machine only.
"""
from pathlib import Path
import argparse,hashlib,shutil,struct,zipfile,json
import lief
import build

RUNTIME_NAME='windows-jre-21.0.12.1.zip'
RUNTIME_SHA256='d35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636'
RUNTIME_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip'

def patch_launcher(path, original, previous_version):
    binary=lief.PE.parse(list(original));major,minor,patch=map(int,build.VERSION.split('.'));version_ms=(major<<16)|minor;version_ls=patch<<16
    icon=(build.ROOT/'resources/app-icon.ico').read_bytes()
    count=struct.unpack_from('<H',icon,4)[0]
    root=binary.resources
    root.delete_child(3);root.delete_child(14)
    icons=lief.PE.ResourceDirectory(3);group=bytearray(struct.pack('<HHH',0,1,count));expected={}
    for n in range(count):
        width,height,colors,reserved,planes,bits,size,offset=struct.unpack_from('<BBBBHHII',icon,6+16*n)
        pixels=icon[offset:offset+size];identifier=n+1
        entry=lief.PE.ResourceDirectory(identifier);data=lief.PE.ResourceData(list(pixels));data.id=1033;entry.add_child(data);icons.add_child(entry)
        group+=struct.pack('<BBBBHHIH',width,height,colors,reserved,planes or 1,bits,size,identifier)
        expected[width or 256]=pixels
    groups=lief.PE.ResourceDirectory(14);entry=lief.PE.ResourceDirectory(101);data=lief.PE.ResourceData(list(group));data.id=1033;entry.add_child(data);groups.add_child(entry)
    root.add_child(icons);root.add_child(groups)
    # Update only our version resources, leaving the original JDK vendor record intact.
    for typ in root.childs:
        if typ.id==16:
            for name in typ.childs:
                for lang in name.childs:
                    content=bytes(lang.content)
                    assert len(previous_version)==len(build.VERSION),'Version resource length changed; use a native Windows rebuild'
                    content=content.replace(previous_version.encode('utf-16-le'),build.VERSION.encode('utf-16-le'))
                    fixed=content.index(struct.pack('<II',0xfeef04bd,0x10000))
                    content=bytearray(content);struct.pack_into('<IIII',content,fixed+8,version_ms,version_ls,version_ms,version_ls)
                    lang.content=bytes(content)
    config=lief.PE.Builder.config_t();config.resources=True
    writer=lief.PE.Builder(binary,config);writer.build();writer.write(str(path))
    verified=lief.PE.parse(path);base=lief.PE.parse(list(original))
    assert verified.optional_header.subsystem==lief.PE.OptionalHeader.SUBSYSTEM.WINDOWS_GUI
    assert len(verified.resources_manager.icons)==count
    for image in verified.resources_manager.icons:assert bytes(image.pixels)==expected[image.width or 256]
    for section in base.sections:
        if section.name in ('.text','.rdata','.pdata'):assert bytes(section.content)==bytes(verified.get_section(section.name).content)
    assert verified.optional_header.addressof_entrypoint==base.optional_header.addressof_entrypoint
    assert verified.resources_manager.manifest==base.resources_manager.manifest
    assert f'{major}-{minor}-{patch}-0' in str(verified.resources_manager.version[0])

def install_runtime(portable,runtime_zip):
    assert hashlib.sha256(runtime_zip.read_bytes()).hexdigest()==RUNTIME_SHA256,'Windows runtime checksum mismatch'
    runtime=portable/'runtime';shutil.rmtree(runtime)
    with zipfile.ZipFile(runtime_zip) as archive:
        prefixes={Path(n).parts[0] for n in archive.namelist()};assert len(prefixes)==1
        for item in archive.infolist():
            relative=Path(*Path(item.filename).parts[1:]);target=runtime/relative
            assert target.resolve().is_relative_to(runtime.resolve())
            if item.is_dir():target.mkdir(parents=True,exist_ok=True)
            else:
                target.parent.mkdir(parents=True,exist_ok=True)
                with archive.open(item) as source,target.open('wb') as output:shutil.copyfileobj(source,output)
    release=(runtime/'release').read_text();assert 'OS_NAME="Windows"' in release
    assert 'java.rmi' in release and 'jdk.charsets' in release and 'jdk.unsupported' in release
    for name in ['bin/jli.dll','bin/server/jvm.dll','bin/vcruntime140.dll','lib/modules']:assert (runtime/name).is_file(),name
    (portable/'RUNTIME-PROVENANCE.json').write_text(json.dumps({'vendor':'Eclipse Adoptium Temurin','version':'21.0.12.1+1','url':RUNTIME_URL,'archive_sha256':RUNTIME_SHA256,'required_modules_tested':build.RUNTIME_MODULES.split(',')},indent=2)+'\n')

def package(base_zip, destination,runtime_zip):
    destination.mkdir(parents=True,exist_ok=True)
    root=build.BUILD/'private-windows';shutil.rmtree(root,ignore_errors=True)
    with zipfile.ZipFile(base_zip) as base:
        for name in base.namelist():
            resolved=(root/name).resolve();assert resolved.is_relative_to(root.resolve())
        base.extractall(root)
        launchers=[name for name in base.namelist() if name.endswith('/BNEL Dicom Cleaner.exe')]
        assert len(launchers)==1
        base_folder=Path(launchers[0]).parent
        original=base.read(launchers[0])
        config=base.read(str(base_folder/'app/BNEL Dicom Cleaner.cfg')).decode()
        previous_version=config.split('-Djpackage.app-version=',1)[1].splitlines()[0].strip()
    portable=root/base_folder
    destination_folder=root/f'BNEL Dicom Cleaner {build.VERSION}'
    if portable!=destination_folder:portable.rename(destination_folder)
    portable=destination_folder
    if runtime_zip is not None and runtime_zip.exists():install_runtime(portable,runtime_zip)
    else:
        # Reuse the complete runtime from a previously delivered verified package.
        provenance=json.loads((portable/'RUNTIME-PROVENANCE.json').read_text())
        assert provenance['archive_sha256']==RUNTIME_SHA256
        manifest={line.split('  ',1)[1]:line.split('  ',1)[0] for line in (portable/'SHA256SUMS.txt').read_text().splitlines()}
        for file in (portable/'runtime').rglob('*'):
            if file.is_file():assert hashlib.sha256(file.read_bytes()).hexdigest()==manifest[file.relative_to(portable).as_posix()]
        release=(portable/'runtime/release').read_text()
        assert 'OS_NAME="Windows"' in release and 'java.rmi' in release
    patch_launcher(portable/'BNEL Dicom Cleaner.exe',original,previous_version)
    for jar in (build.BUILD/'app').glob('*.jar'):shutil.copy2(jar,portable/'app'/jar.name)
    cfg=portable/'app/BNEL Dicom Cleaner.cfg'
    cfg.write_text('[Application]\napp.classpath=$APPDIR\\BNEL-Dicom-Cleaner.jar\napp.mainclass=org.bnel.dicom.Main\n'+''.join('app.classpath=$APPDIR\\'+name+'\n' for name in build.JARS)+'\n[JavaOptions]\njava-options=-Djpackage.app-version='+build.VERSION+'\njava-options=-XX:MaxRAMPercentage=65\n',encoding='utf-8')
    shutil.copytree(build.BUILD/'notices',portable/'THIRD-PARTY-NOTICES',dirs_exist_ok=True)
    metadata=portable/'app/.jpackage.xml'
    if metadata.exists():metadata.write_text(metadata.read_text().replace(previous_version,build.VERSION))
    # Keep provenance and verification material on the build machine only.
    # The deliverable contains only the executable, runtime/application dependencies,
    # and license notices; no source, build tools, settings, previews or sample data.
    # Record package contents without source paths, credentials, or patient data.
    checksums=[]
    for file in sorted(portable.rglob('*')):
        if file.is_file() and file.name!='SHA256SUMS.txt':checksums.append(hashlib.sha256(file.read_bytes()).hexdigest()+'  '+file.relative_to(portable).as_posix())
    (portable/'SHA256SUMS.txt').write_text('\n'.join(checksums)+'\n')
    archive=destination/f'BNEL-Dicom-Cleaner-{build.VERSION}-Windows-x64.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        for file in sorted(portable.rglob('*')):
            relative=file.relative_to(portable)
            if not file.is_file():continue
            allowed=relative.parts[0] in ('app','runtime','THIRD-PARTY-NOTICES') or relative.as_posix() in ('BNEL Dicom Cleaner.exe','LICENSE.txt')
            if allowed and file.name!='dependency-sha256.json':out.write(file,portable.name+'/'+relative.as_posix())
    with zipfile.ZipFile(archive) as test:assert test.testzip() is None
    print(archive);print('Bytes:',archive.stat().st_size);print('SHA256:',hashlib.sha256(archive.read_bytes()).hexdigest())

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('base_zip',type=Path);parser.add_argument('destination',type=Path);parser.add_argument('--runtime',type=Path,default=build.BUILD/'downloads'/RUNTIME_NAME);args=parser.parse_args();package(args.base_zip,args.destination,args.runtime)
