#!/usr/bin/env python3
"""Emulator-only lifecycle checks. Device tests use non-destructive instrumentation."""
import hashlib,json,os,secrets,subprocess,time,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):
    return subprocess.check_output(['adb',*args],text=True).strip()
assert adb('shell','getprop','ro.kernel.qemu')=='1', 'Only run on a disposable emulator'
package='br.eng.thsouza.winterhold'
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('shell','am','force-stop',package)
password=secrets.token_hex(20);salt=secrets.token_hex(24)
account={'id':'ci-lifecycle','name':'CI lifecycle','username':'ci-lifecycle','salt':salt,
         'hash':hashlib.pbkdf2_hmac('sha256',password.encode(),salt.encode(),210000,32).hex(),'active':True,'legacyUntil':0}
root=ET.Element('map');ET.SubElement(root,'string',name='accounts_v2').text=json.dumps([account])
xml=Path('app/build/ci-web-access.xml');ET.ElementTree(root).write(xml,encoding='utf-8',xml_declaration=True)
adb('push',str(xml),'/data/local/tmp/ci-web-access.xml')
adb('shell','run-as',package,'mkdir','-p','shared_prefs')
adb('shell','run-as',package,'cp','/data/local/tmp/ci-web-access.xml','shared_prefs/web_access.xml')
adb('shell','dumpsys','deviceidle','whitelist','+'+package)
if int(adb('shell','getprop','ro.build.version.sdk'))>=33:adb('shell','pm','grant',package,'android.permission.POST_NOTIFICATIONS')
adb('shell','am','start','-W','-n',package+'/.MainActivity')
adb('forward','tcp:18790','tcp:8790')
cookie=''
def request(path,body=None):
    headers={'X-Winterhold-Request':'1','Cookie':cookie,'Content-Type':'application/json'}
    req=urllib.request.Request('http://127.0.0.1:18790/api/v1/'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    with urllib.request.urlopen(req,timeout=10) as response:
        return json.load(response),response.headers.get('Set-Cookie')
def login():
    global cookie
    _,value=request('login',{'username':'ci-lifecycle','password':password});cookie=value.split(';')[0]
def wait_for(check,seconds=120):
    deadline=time.monotonic()+seconds;last=None
    while time.monotonic()<deadline:
        try:
            if check():return
        except Exception as error:last=error
        time.sleep(2)
    raise AssertionError('Lifecycle condition timed out') from last
wait_for(lambda: (login() or True))
request('action',{'type':'mode','value':'demo','acknowledgeRealDelivery':True})
request('action',{'type':'running','value':False})
request('action',{'type':'limit','key':'temperature','value':29})
def recovered():
    login();state,_=request('state');return state['limits']['temperature']==29 and state['running'] is False
# No force-stop: emulate a system kill and require START_STICKY recovery.
adb('root');adb('wait-for-device')
adb('forward','tcp:18790','tcp:8790')
pid=adb('shell','pidof',package);adb('shell','kill','-9',pid)
wait_for(recovered)
print('START_STICKY and persisted settings passed',flush=True)
# Android 11 persists the PackageManager "stopped/not launched" flags asynchronously.
# Reboot only after the real activity launch is durable; otherwise an immediate
# emulator reboot can restore the old force-stopped flag and skip BOOT_COMPLETED.
def launch_state_persisted():
    restriction=ET.fromstring(adb('shell','cat','/data/system/users/0/package-restrictions.xml'))
    entry=next((item for item in restriction.findall('pkg') if item.get('name')==package),None)
    return entry is not None and entry.get('stopped')!='true' and entry.get('nl')!='true'
wait_for(launch_state_persisted,60)
adb('reboot');adb('wait-for-device')
wait_for(lambda:adb('shell','getprop','sys.boot_completed')=='1',180)
adb('shell','wm','dismiss-keyguard')
adb('forward','tcp:18790','tcp:8790')
wait_for(recovered,240)
print('BOOT_COMPLETED and password login after reboot passed',flush=True)
