"""Icon-only repack from a known working portable ZIP; preserves the launcher layout.

Requires pefile and Pillow on the build machine. Never adds PE sections or
rewrites code, imports, version information, manifest or launcher configuration.
"""
from pathlib import Path
from io import BytesIO
import argparse, copy, hashlib, json, struct, zipfile
import pefile
from PIL import Image

SIZES = (16, 24, 32, 48, 64, 128, 256)

def align(value, boundary):
    return (value + boundary - 1) // boundary * boundary

def icon_frames(path):
    raw = path.read_bytes()
    assert struct.unpack_from('<HH', raw) == (0, 1)
    frames = {}
    for i in range(struct.unpack_from('<H', raw, 4)[0]):
        w,h,colors,reserved,planes,bits,size,offset = struct.unpack_from('<BBBBHHII', raw, 6+16*i)
        payload = raw[offset:offset+size]
        if payload.startswith(b'\x89PNG'):
            actual = Image.open(BytesIO(payload)).size
        else:
            actual = (struct.unpack_from('<i', payload, 4)[0], struct.unpack_from('<i', payload, 8)[0]//2)
        # ICO dimensions 0 mean 256. Reject the supplied 512 bitmap mislabeled 256.
        if actual != (w or 256, h or 256) or actual[0] not in SIZES:
            continue
        frames[actual[0]] = payload
    assert set(frames) == set(SIZES), 'Missing valid icon resolution'
    payload = frames[256]
    one = struct.pack('<HHH',0,1,1)+struct.pack('<BBBBHHII',0,0,0,0,1,32,len(payload),22)+payload
    window = Image.open(BytesIO(one)).convert('RGBA')
    png = BytesIO(); window.save(png,format='PNG',compress_level=9)
    frames[256] = png.getvalue()
    return frames, png.getvalue()

def patch_exe(original, frames):
    pe = pefile.PE(data=original)
    directory = pe.OPTIONAL_HEADER.DATA_DIRECTORY[2]
    section = pe.get_section_by_rva(directory.VirtualAddress)
    assert section is pe.sections[-1], 'Resource section must be last'
    assert directory.VirtualAddress == section.VirtualAddress
    assert section.PointerToRawData + section.SizeOfRawData == len(original)
    assert pe.OPTIONAL_HEADER.DATA_DIRECTORY[4].Size == 0, 'Signed launcher needs a native signed rebuild'
    leaves = []
    def walk(tree, path=()):
        for entry in tree.entries:
            assert entry.name is None
            route = path+(entry.id,)
            if hasattr(entry,'directory'): walk(entry.directory,route)
            else:
                record = entry.data.struct
                leaves.append((route,record,pe.get_data(record.OffsetToData,record.Size)))
    walk(pe.DIRECTORY_ENTRY_RESOURCE)
    icons = sorted((x for x in leaves if x[0][0] == 3),key=lambda x:x[0][1])
    assert len(icons)==len(SIZES)
    replacements = {entry[0]:frames[size] for entry,size in zip(icons,SIZES)}
    groups = [x for x in leaves if x[0][0] == 14]
    assert len(groups)==1
    group = bytearray(struct.pack('<HHH',0,1,len(SIZES)))
    for entry,size in zip(icons,SIZES):
        group += struct.pack('<BBBBHHIH',size%256,size%256,0,0,1,32,len(frames[size]),entry[0][1])
    replacements[groups[0][0]] = bytes(group)
    start = min(record.OffsetToData-section.VirtualAddress for _,record,_ in leaves)
    block = bytearray(original[section.PointerToRawData:section.PointerToRawData+start])
    for route,record,old in leaves:
        block += b'\0' * (align(len(block),4)-len(block))
        data = replacements.get(route,old)
        struct.pack_into('<II',block,record.get_file_offset()-section.PointerToRawData,section.VirtualAddress+len(block),len(data))
        block += data
    actual_size = len(block)
    raw_size = max(section.SizeOfRawData,align(actual_size,pe.OPTIONAL_HEADER.FileAlignment))
    block += b'\0' * (raw_size-len(block))
    updated = bytearray(original[:section.PointerToRawData])+block
    struct.pack_into('<I',updated,section.get_file_offset()+8,actual_size)
    struct.pack_into('<I',updated,section.get_file_offset()+16,raw_size)
    struct.pack_into('<I',updated,directory.get_file_offset()+4,actual_size)
    struct.pack_into('<I',updated,pe.OPTIONAL_HEADER.get_field_absolute_offset('SizeOfImage'),align(section.VirtualAddress+actual_size,pe.OPTIONAL_HEADER.SectionAlignment))
    struct.pack_into('<I',updated,pe.OPTIONAL_HEADER.get_field_absolute_offset('SizeOfInitializedData'),pe.OPTIONAL_HEADER.SizeOfInitializedData+raw_size-section.SizeOfRawData)
    check = pefile.PE(data=bytes(updated))
    struct.pack_into('<I',updated,pe.OPTIONAL_HEADER.get_field_absolute_offset('CheckSum'),check.generate_checksum())
    check = pefile.PE(data=bytes(updated))
    assert len(check.sections)==len(pe.sections)
    for old,new in zip(pe.sections[:-1],check.sections[:-1]):
        assert old.get_data()==new.get_data() and old.__pack__()==new.__pack__()
    for t in check.DIRECTORY_ENTRY_RESOURCE.entries:
        for n in t.directory.entries:
            for lang in n.directory.entries:
                route=(t.id,n.id,lang.id); record=lang.data.struct
                before=next(data for key,_,data in leaves if key==route)
                assert check.get_data(record.OffsetToData,record.Size)==replacements.get(route,before)
    return bytes(updated)

def package(source, artwork, destination):
    frames,png=icon_frames(artwork)
    with zipfile.ZipFile(source) as base:
        exe=next(n for n in base.namelist() if n.endswith('/BNEL Dicom Cleaner.exe'))
        prefix=exe.rsplit('/',1)[0]
        jar_name=prefix+'/app/BNEL-Dicom-Cleaner.jar'
        cfg_name=prefix+'/app/BNEL Dicom Cleaner.cfg'
        assert cfg_name in base.namelist(), 'Missing launcher config'
        updated_exe=patch_exe(base.read(exe),frames)
        jar=BytesIO()
        with zipfile.ZipFile(BytesIO(base.read(jar_name))) as old,zipfile.ZipFile(jar,'w') as new:
            for info in old.infolist():
                new.writestr(copy.copy(info),png if info.filename=='app-icon.png' else old.read(info))
        destination.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(destination,'w') as out:
            for info in base.infolist():
                data=updated_exe if info.filename==exe else jar.getvalue() if info.filename==jar_name else base.read(info)
                out.writestr(copy.copy(info),data)
    with zipfile.ZipFile(source) as old,zipfile.ZipFile(destination) as new:
        assert old.namelist()==new.namelist()
        changed=[n for n in old.namelist() if old.read(n)!=new.read(n)]
        assert set(changed)=={exe,jar_name}
        assert new.read(cfg_name)==old.read(cfg_name)
        with zipfile.ZipFile(BytesIO(old.read(jar_name))) as a,zipfile.ZipFile(BytesIO(new.read(jar_name))) as b:
            assert a.namelist()==b.namelist()
            assert [n for n in a.namelist() if a.read(n)!=b.read(n)]==['app-icon.png']
    return {'changed_files':changed,'icon_sizes':list(SIZES),'configuration_unchanged':True,'all_other_package_files_unchanged':True,'launcher_code_and_other_sections_unchanged':True,'bytes':destination.stat().st_size,'sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),'windows_launch_test':'Not available in this Linux environment'},png

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path);p.add_argument('artwork',type=Path);p.add_argument('destination',type=Path)
    args=p.parse_args(); report,_=package(args.source,args.artwork,args.destination)
    print(json.dumps(report,indent=2))
