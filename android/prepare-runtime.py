"""Pinned Android/ARM64 Node packages; no installed Termux is used by the APK.
pip install lief==0.17.6; npm ci --prefix backend/whatsapp --omit=optional --ignore-scripts
python android/prepare-runtime.py
"""
import hashlib
import io
import json
from pathlib import Path
import tarfile
import urllib.request
import zipfile
import lief

root = Path(__file__).resolve().parent
lock = json.loads((root / 'runtime-lock.json').read_text())
cache = root.parent / '.work/runtime'
cache.mkdir(parents=True, exist_ok=True)
output = root / 'app/src/main/jniLibs/arm64-v8a'
output.mkdir(parents=True, exist_ok=True)
libs = {}
aliases = {}
cert = None
certificates = []
notices = {}
for package in lock['packages']:
    archive = cache / Path(package['Filename']).name.replace(':', '_')
    if not archive.exists():
        print('Downloading', package['Package'], flush=True)
        urllib.request.urlretrieve(lock['repository'] + package['Filename'], archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == package['SHA256'], archive
    data = archive.read_bytes()
    assert data[:8] == b'!<arch>\n'
    position = 8
    while position < len(data):
        header = data[position:position + 60]
        size = int(header[48:58])
        name = header[:16].decode().strip().rstrip('/')
        payload = data[position + 60:position + 60 + size]
        position += 60 + size + size % 2
        if not name.startswith('data.tar'):
            continue
        with tarfile.open(fileobj=io.BytesIO(payload)) as tar:
            for member in tar.getmembers():
                if member.issym() and '/lib/lib' in member.name:
                    aliases[Path(member.name).name] = Path(member.linkname).name
                if not member.isfile():
                    continue
                filename = member.name
                if filename.endswith('/bin/node') or '/lib/lib' in filename and '.so' in filename:
                    content = tar.extractfile(member).read()
                    if content[:4] == b'\x7fELF':
                        libs[Path(filename).name] = content
                if filename.endswith('/etc/tls/cert.pem'):
                    cert = tar.extractfile(member).read()
                elif '/share/ca-certificates/' in filename and filename.endswith('.crt'):
                    certificates.append(tar.extractfile(member).read())
                if '/share/doc/' in filename and any(x in filename.lower() for x in ['copyright', 'license']):
                    notices[package['Package'] + '-' + Path(filename).name] = tar.extractfile(member).read()

# Rewrite all private dependencies to APK-compatible lib*.so names. Android's installer
# extracts only those names. Strip Termux RUNPATHs; never search another app's directory.
parsed = {name: lief.ELF.parse(list(content)) for name, content in libs.items()}
sonames = {}
for name, elf in parsed.items():
    sonames[name] = name.split('.so')[0] + '.so' if name != 'node' else 'libwinterhold_node.so'
    for entry in elf.dynamic_entries:
        if isinstance(entry, lief.ELF.DynamicSharedObject):
            sonames[entry.name] = sonames[name]
system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so'}
for alias, target in aliases.items():
    if target in sonames:
        sonames[alias] = sonames[target]
needed = ['node']
visited = set()
while needed:
    name = needed.pop()
    if name in visited:
        continue
    visited.add(name)
    elf = parsed[name]
    for entry in list(elf.dynamic_entries):
        if isinstance(entry, lief.ELF.DynamicEntryLibrary) and entry.name not in system:
            original = entry.name
            assert original in sonames, original
            target = sonames[original]
            match = next(n for n in parsed if sonames[n] == target)
            needed.append(match)
            entry.name = target
        elif isinstance(entry, (lief.ELF.DynamicEntryRunPath, lief.ELF.DynamicEntryRpath)):
            elf.remove(entry)
        elif isinstance(entry, lief.ELF.DynamicSharedObject):
            entry.name = sonames[name]
    elf.write(str(output / sonames[name]))
assets = root / 'app/src/main/assets/bridge'
assets.mkdir(parents=True, exist_ok=True)
project = root.parent / 'backend/whatsapp'
assert (project / 'node_modules').is_dir(), 'Run npm ci first'
with zipfile.ZipFile(assets / 'bridge.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
    for name in ['server.js', 'package.json', 'package-lock.json']:
        bundle.write(project / name, name)
    for file in (project / 'node_modules').rglob('*'):
        if file.is_file() and '.bin' not in file.parts:
            assert file.suffix != '.node', f'Unexpected native npm addon: {file}'
            bundle.write(file, file.relative_to(project).as_posix())
    cert = cert or b'\n'.join(certificates)
    assert cert, 'CA bundle missing'
    bundle.writestr('ca.pem', cert)
    for name, content in notices.items():
        bundle.writestr('licenses/' + name, content)
    bundle.write(root / 'runtime-lock.json', 'runtime-lock.json')
print('Prepared', len(visited), 'ELFs, bridge bytes', (assets / 'bridge.zip').stat().st_size)
