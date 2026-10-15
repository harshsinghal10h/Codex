#!/usr/bin/env python3
"""Verify payload preservation, the native Settings hook, and one launcher."""
import argparse
from pathlib import Path
from zipfile import ZipFile
from loguru import logger
logger.remove()
from androguard.core.axml import AXMLPrinter
from androguard.core.dex import DEX

SETTINGS='Lorg/chromium/chrome/browser/settings/BraveSettingsActivity;'
HOOK='Ldev/harsh/protonbridge/BraveVpnMenuHook;'
NS='{http://schemas.android.com/apk/res/android}'

def instructions(method):
    return [(i.get_name(),i.get_output()) for i in method.get_instructions()]

def methods(dex):
    return {(c.get_name(),m.get_name(),m.get_descriptor()):m
            for c in dex.get_classes() for m in c.get_methods()}

def launchers(app):
    result=set()
    for item in app:
        if item.tag not in ('activity','activity-alias'): continue
        for intent in item.findall('intent-filter'):
            actions={n.get(NS+'name') for n in intent.findall('action')}
            categories={n.get(NS+'name') for n in intent.findall('category')}
            if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in categories:
                result.add(item.get(NS+'name'))
    return result

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--original',type=Path,required=True); p.add_argument('--apk',type=Path,required=True)
    a=p.parse_args()
    with ZipFile(a.original) as original,ZipFile(a.apk) as integrated:
        checked=0
        for name in original.namelist():
            if name in ('AndroidManifest.xml','classes5.dex') or name.startswith('META-INF/'): continue
            assert original.read(name)==integrated.read(name),'Unexpected original payload change: '+name
            checked+=1
        root=AXMLPrinter(integrated.read('AndroidManifest.xml')).get_xml_obj()
        app=root.find('application')
        original_app=AXMLPrinter(original.read('AndroidManifest.xml')).get_xml_obj().find('application')
        assert launchers(app)==launchers(original_app) and len(launchers(app))==1,'Expected one original Brave launcher'
        assert root.get(NS+'versionName')=='1.97.56-proton.2'
        assert root.get(NS+'versionCode')=='429705606'
        controls='dev.harsh.protonbridge.ProtonVpnActivity'
        aliases=[n for n in app.findall('activity-alias') if n.get(NS+'targetActivity')==controls]
        assert len(aliases)==8
        control=next(n for n in app.findall('activity') if n.get(NS+'name')==controls)
        assert control.get(NS+'exported')=='false' and not control.findall('intent-filter')
        service=next(n for n in app.findall('service') if n.get(NS+'name')=='dev.harsh.protonbridge.ProtonVpnService')
        assert service.get(NS+'permission')=='android.permission.BIND_VPN_SERVICE'
        assert service.get(NS+'exported')=='false'
        assert service.get(NS+'foregroundServiceType')=='0x00000400'
        for n in app.findall('service'):
            if n.get(NS+'name') in ('org.chromium.chrome.browser.vpn.wireguard.WireguardService',
                    'com.wireguard.android.backend.GoBackend$VpnService'):
                assert n.get(NS+'enabled')=='false'
        before=DEX(original.read('classes5.dex')); after=DEX(integrated.read('classes5.dex'))
        old_classes={c.get_name() for c in before.get_classes()}
        new_classes={c.get_name() for c in after.get_classes()}
        assert old_classes-new_classes=={SETTINGS} and not new_classes-old_classes
        old_methods=methods(before); new_methods=methods(after)
        assert set(new_methods)=={k for k in old_methods if k[0]!=SETTINGS}
        for key,method in new_methods.items():
            assert instructions(method)==instructions(old_methods[key]),'Changed original method: '+str(key)
        moved=DEX(integrated.read('classes8.dex'))
        assert {c.get_name() for c in moved.get_classes()}=={SETTINGS}
        moved_methods=methods(moved)
        assert set(moved_methods)=={k for k in old_methods if k[0]==SETTINGS}
        for key,method in moved_methods.items():
            if key[1]!='onCreateOptionsMenu':
                assert instructions(method)==instructions(old_methods[key]),'Changed moved method: '+str(key)
            else:
                code=instructions(method)
                assert sum(HOOK+'->addMenu' in value for _,value in code)==1
                assert code[-2:]==[('const/4','v2, 1'),('return','v2')],code[-2:]
        added=DEX(integrated.read('classes7.dex'))
        assert all(c.get_name().startswith('Ldev/harsh/protonbridge/') for c in added.get_classes())
        assert HOOK in {c.get_name() for c in added.get_classes()}
    print('PASS:',checked,'original payloads unchanged;',len(new_methods),'original methods preserved; one launcher; internal Proton Settings menu verified.')

if __name__=='__main__': main()
