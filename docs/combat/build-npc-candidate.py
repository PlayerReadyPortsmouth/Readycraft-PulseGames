"""Build the single-class NPC candidate from a verified upstream jar and patched v2.7.0 checkout.

Apply ocm-npc.patch to the pinned checkout first. Pass a Paper API plus its library
jars as --classpath (platform-separated), and a Java 25 javac executable.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--upstream-jar', type=Path, required=True)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--classpath', required=True)
parser.add_argument('--javac', default='javac')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
expected = 'f68f52c956103e6081dbf2d737ddf70436042e77997c9ae20eb5aaf87b263603'
if hashlib.sha256(args.upstream_jar.read_bytes()).hexdigest() != expected:
    raise SystemExit('Upstream jar checksum differs from the tested 2.7.0 binary')
if args.output.resolve() == args.upstream_jar.resolve():
    raise SystemExit('Choose a separate output file')
name = 'kernitus/plugin/OldCombatMechanics/module/OCMModule'
source = args.source_root / 'src/main/java' / (name + '.java')
if 'pulsegames-classic-combat' not in source.read_text():
    raise SystemExit('Apply the included NPC patch first')
with tempfile.TemporaryDirectory(prefix='pulse-ocm-') as directory:
    subprocess.run([args.javac, '--release', '8', '-cp',
                    str(args.upstream_jar) + os.pathsep + args.classpath,
                    '-d', directory, str(source)], check=True)
    with zipfile.ZipFile(args.upstream_jar) as old, zipfile.ZipFile(args.output, 'w') as new:
        for info in old.infolist():
            content = old.read(info.filename)
            if info.filename == name + '.class':
                content = (Path(directory) / (name + '.class')).read_bytes()
            if info.filename == 'plugin.yml':
                content = content.replace(b'version: 2.7.0', b'version: 2.7.0-ready.1')
            new.writestr(info, content)
print(hashlib.sha256(args.output.read_bytes()).hexdigest(), args.output)
