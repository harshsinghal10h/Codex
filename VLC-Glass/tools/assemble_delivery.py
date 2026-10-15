#!/usr/bin/env python3
"""Reassemble the supplied release bytes, verify them, and expose their source."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def checked_path(base, relative):
    rel = pathlib.PurePosixPath(relative)
    if rel.is_absolute() or '..' in rel.parts:
        raise ValueError('Unsafe relative path')
    path = base.joinpath(*rel.parts)
    if not path.resolve().is_relative_to(base.resolve()):
        raise ValueError('Path escapes delivery directory')
    return path


def verify(data, expected):
    if len(data) != expected['bytes']:
        raise ValueError('Byte count mismatch: ' + expected.get('name', expected.get('path', 'artifact')))
    if hashlib.sha256(data).hexdigest() != expected['sha256']:
        raise ValueError('SHA-256 mismatch: ' + expected.get('name', expected.get('path', 'artifact')))


def main():
    manifest = json.loads((ROOT / '.delivery/manifest.json').read_text())
    downloads = ROOT / 'downloads'
    downloads.mkdir(exist_ok=True)
    sums = []
    for artifact in manifest['files']:
        data = bytearray()
        for part in artifact['parts']:
            block = checked_path(ROOT, part['path']).read_bytes()
            verify(block, part)
            data.extend(block)
        verify(data, artifact)
        blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        if blob != artifact['git_blob_sha']:
            raise ValueError('Git blob identity mismatch')
        output = checked_path(downloads, artifact['name'])
        if output.parent != downloads:
            raise ValueError('Artifact name must be a basename')
        output.write_bytes(data)
        sums.append(artifact['sha256'] + '  ' + artifact['name'])
        print('Verified:', artifact['name'], len(data), artifact['sha256'], flush=True)
    (downloads / 'SHA256SUMS.txt').write_text('\n'.join(sums) + '\n')

    source = ROOT / 'source'
    source.mkdir(exist_ok=True)
    with zipfile.ZipFile(downloads / 'VLC-Glass-Source-0.2.0.zip') as archive:
        for entry in archive.infolist():
            rel = pathlib.PurePosixPath(entry.filename)
            if not rel.parts or rel.parts[0] != 'VLC-Glass' or rel.is_absolute() or '..' in rel.parts:
                raise ValueError('Unexpected source archive path')
            if entry.is_dir():
                continue
            path = checked_path(source, pathlib.PurePosixPath(*rel.parts[1:]).as_posix())
            if path.suffix in {'.p12', '.jks', '.keystore'} or 'private' in path.relative_to(source).parts:
                raise ValueError('Signing material must not be published')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(entry))
    (source / '.gitignore').write_text('build/\ndist/\nprivate/\nnode_modules/\n__pycache__/\n*.pyc\n')
    (source / 'dist').mkdir(exist_ok=True)
    shutil.copyfile(downloads / 'VLC-Glass-Android-0.2.0-arm64.apk', source / 'dist/VLC-Glass-Android-0.2.0-arm64.apk')
    for filename in ['app.js', 'player.js', 'glass.js', 'host.js', 'deck.js']:
        subprocess.run(['node', '--check', str(source / 'assets/ui' / filename)], check=True)
    subprocess.run(['python3', str(source / 'tests/verify_package.py')], check=True)
    with zipfile.ZipFile(downloads / 'VLC-Glass-Android-0.2.0-arm64.apk') as archive:
        for path in (source / 'assets').rglob('*'):
            if path.is_file() and archive.read(path.relative_to(source).as_posix()) != path.read_bytes():
                raise ValueError('APK UI asset differs from published source')
        for path in (source / 'vendor/lib').rglob('*.so'):
            if archive.read(path.relative_to(source / 'vendor').as_posix()) != path.read_bytes():
                raise ValueError('Native library differs from published source')
    print('PASS: release bytes, source assets and native libraries agree', flush=True)


if __name__ == '__main__':
    main()
