from pathlib import Path
from io import BytesIO
import hashlib,json,zipfile,struct,lief
root=Path.cwd();release=root/'release/BNEL-Dicom-Cleaner-1.1.0-Windows-x64.zip';base=root/'refresh-inputs/BNEL-Dicom-Cleaner-1.0.0-Windows-x64.zip'
with zipfile.ZipFile(release) as z,zipfile.ZipFile(base) as old:
 prefix='BNEL Dicom Cleaner 1.1.0/';prior='BNEL Dicom Cleaner 1.0.0/'
 assert z.testzip() is None
 names=z.namelist();assert all(n.startswith(prefix) for n in names)
 relatives=[n[len(prefix):] for n in names]
 assert set(n.split('/')[0] for n in relatives)=={'BNEL Dicom Cleaner.exe','LICENSE.txt','app','runtime','THIRD-PARTY-NOTICES'}
 assert not any(any(k in n for k in ['SOURCE.zip','PREVIEWS/','BNEL-settings.properties','.dcm','test-classes','screenshots','.java']) for n in relatives)
 for n in relatives:
  if n.startswith('runtime/') or n=='app/bnel-recycle.dll':assert z.read(prefix+n)==old.read(prior+n),n
 cfg=z.read(prefix+'app/BNEL Dicom Cleaner.cfg').decode();assert '-Djpackage.app-version=1.1.0' in cfg
 assert 'java.rmi' in z.read(prefix+'runtime/release').decode()
 with zipfile.ZipFile(BytesIO(z.read(prefix+'app/BNEL-Dicom-Cleaner.jar'))) as app:
  assert 'org/bnel/dicom/Main.class' in app.namelist() and not any('Test.class' in n or n.endswith('.java') for n in app.namelist())
  assert app.read('app-icon.png')==(root/'dicomcleaner/standalone/resources/app-icon.png').read_bytes()
  assert 'app-icon.ico' not in app.namelist()
 for name,expected in json.loads((root/'dicomcleaner/standalone/vendor-lock.json').read_text()).items():assert hashlib.sha256(z.read(prefix+'app/'+name)).hexdigest()==expected
 binary=lief.PE.parse(list(z.read(prefix+'BNEL Dicom Cleaner.exe')));original=lief.PE.parse(list(old.read(prior+'BNEL Dicom Cleaner.exe')))
 assert binary.optional_header.subsystem==lief.PE.OptionalHeader.SUBSYSTEM.WINDOWS_GUI
 for section in ['.text','.rdata','.pdata']:assert bytes(binary.get_section(section).content)==bytes(original.get_section(section).content)
 assert binary.optional_header.addressof_entrypoint==original.optional_header.addressof_entrypoint
 assert binary.resources_manager.manifest==original.resources_manager.manifest
 ico=(root/'dicomcleaner/standalone/resources/app-icon.ico').read_bytes();expected={}
 for i in range(struct.unpack_from('<H',ico,4)[0]):
  width,height,colors,reserved,planes,bits,size,offset=struct.unpack_from('<BBBBHHII',ico,6+16*i);expected[width or 256]=ico[offset:offset+size]
 assert set(expected)=={16,24,32,48,64,128,256}
 for icon in binary.resources_manager.icons:assert bytes(icon.pixels)==expected[icon.width or 256]
 assert len(binary.resources_manager.icons)==7
 record={'version':'1.1.0','bytes':release.stat().st_size,'sha256':hashlib.sha256(release.read_bytes()).hexdigest(),'files':len(names),'runtime_unchanged':True,'java_rmi_included':True,'gui_subsystem':True,'launcher_code_unchanged':True,'embedded_icon_sizes':sorted(expected),'icon_source_sha256':hashlib.sha256((root/'upload/Icon_reference.png').read_bytes()).hexdigest(),'lean_contents':True,'windows_execution':'Not available in this environment'}
 (root/'release-audit.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
