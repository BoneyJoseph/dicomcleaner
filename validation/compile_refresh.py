from pathlib import Path
import sys,shutil
sys.path.insert(0,'dicomcleaner/standalone');import build
cp=':'.join(str(build.VENDOR/n) for n in build.JARS)
build.run([*build.java_tool('javac'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',build.BUILD/'classes',*sorted((build.ROOT/'src').rglob('*.java'))])
for r in (build.ROOT/'resources').iterdir():
 if r.is_file():shutil.copy2(r,build.BUILD/'classes'/r.name)
build.run([*build.java_tool('javac'),'--release','17','-encoding','UTF-8','-cp',str(build.BUILD/'classes')+':'+cp,'-d',build.BUILD/'test-classes',*sorted((build.ROOT/'test').rglob('*.java'))])
