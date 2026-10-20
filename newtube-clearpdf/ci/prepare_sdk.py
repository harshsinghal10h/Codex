#!/usr/bin/env python3
"""Install the pinned public Android SDK packages used by this project."""
import hashlib, pathlib, shutil, tempfile, urllib.request, zipfile
from xml.dom import minidom, Node
import sys
root=pathlib.Path(sys.argv[1]).resolve()
root.mkdir(parents=True,exist_ok=True)
repository=urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml').read()
catalog=minidom.parseString(repository)
packages=[('platforms;android-37.0','platform-37.0_r02.zip','platforms','android-37.0'),
          ('build-tools;36.0.0','build-tools_r36_linux.zip','build-tools','36.0.0')]
for package,archive,parent,name in packages:
    with tempfile.TemporaryDirectory() as temporary:
        temporary=pathlib.Path(temporary)
        path=temporary/archive
        urllib.request.urlretrieve('https://dl.google.com/android/repository/'+archive,path)
        extracted=temporary/'extracted'
        with zipfile.ZipFile(path) as z: z.extractall(extracted)
        source=next(p for p in extracted.iterdir() if p.is_dir())
        target=root/parent/name
        target.parent.mkdir(parents=True,exist_ok=True)
        if target.exists(): shutil.rmtree(target)
        shutil.move(str(source),str(target))
        for executable in ['aapt','aapt2','aidl','apksigner','d8','zipalign']:
            candidate=target/executable
            if candidate.exists(): candidate.chmod(candidate.stat().st_mode|0o111)
    remote=next(p for p in catalog.getElementsByTagName('remotePackage') if p.getAttribute('path')==package)
    document=minidom.getDOMImplementation().createDocument('http://schemas.android.com/repository/android/common/02','ciCommon:repository',None)
    parent_node=document.documentElement
    parent_node.setAttribute('xmlns:ciCommon','http://schemas.android.com/repository/android/common/02')
    for i in range(catalog.documentElement.attributes.length):
        attribute=catalog.documentElement.attributes.item(i)
        if attribute.name.startswith('xmlns'): parent_node.setAttribute(attribute.name,attribute.value)
    local=document.importNode(remote,True)
    document.renameNode(local,None,'localPackage')
    for child in list(local.childNodes):
        if child.nodeType==Node.ELEMENT_NODE and child.tagName in ['archives','channelRef']: local.removeChild(child)
    parent_node.appendChild(local)
    (target/'package.xml').write_bytes(document.toxml(encoding='utf-8'))
    print('Installed',package)
licenses=root/'licenses'; licenses.mkdir(exist_ok=True)
for item in catalog.getElementsByTagName('license'):
    text=''.join(n.data for n in item.childNodes if n.nodeType in [Node.TEXT_NODE,Node.CDATA_SECTION_NODE])
    (licenses/item.getAttribute('id')).write_text(hashlib.sha1(text.encode()).hexdigest()+'\n'+hashlib.sha1(text.strip().encode()).hexdigest()+'\n')
with (licenses/'android-sdk-license').open('a') as output:
    output.write('24333f8a63b6825ea9c5514f83c2829b004d1fee\nd56f5187479451eabf01fb78af6dfcb131a6481e\n8933bad161af4178b1185d1a37fbf41ea5269c55\n')
