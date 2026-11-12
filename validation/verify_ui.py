from pathlib import Path
import os, socket, subprocess, time, sys
root=Path(__file__).resolve().parent
native=root/'refresh-native'
app=root/'dicomcleaner/standalone'
display=os.environ.get('BNEL_TEST_DISPLAY','195')
env=dict(os.environ, DISPLAY='127.0.0.1:'+display,LD_LIBRARY_PATH=str(native/'usr/lib/x86_64-linux-gnu'),XDG_DATA_DIRS=str(native/'usr/share')+':/usr/share')
log=(root/('headful-display-'+display+'.log')).open('w')
server=subprocess.Popen([str(root/'refresh-native-v2/usr/bin/Xvfb'),':'+display,'-screen','0','3840x2560x24','-nolisten','local','-nolisten','unix','-listen','tcp','-ac'],env=env,stdout=log,stderr=log)
wm=None
try:
    for n in range(100):
        try:
            with socket.create_connection(('127.0.0.1',6000+int(display)),timeout=.1):break
        except OSError:time.sleep(.1)
    wm=subprocess.Popen([str(native/'usr/bin/openbox'),'--sm-disable','--config-file',str(native/'etc/xdg/openbox/rc.xml')],env=env,stdout=log,stderr=log)
    time.sleep(.5)
    cp=':'.join([str(app/'build/test-classes'),str(app/'build/classes'),str(app/'vendor/*')])
    extra=[]
    if os.environ.get('BNEL_TEST_SCALE'):extra=['-Dflatlaf.uiScale='+os.environ['BNEL_TEST_SCALE']]
    subprocess.run(['java',*extra,'-cp',cp,'org.bnel.dicom.'+(sys.argv[1] if len(sys.argv)>1 else 'GuiTest'),str(app/'build/screenshots')],env=env,check=True)
finally:
    if wm:wm.terminate();wm.wait()
    server.terminate();server.wait();log.close()
