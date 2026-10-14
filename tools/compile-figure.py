#!/usr/bin/env python3
"""Compile the supplied SVG paths into a small, dependency-free native vector asset."""
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
svg = ET.parse(root / 'display-engine.svg').getroot()
shapes = []
group = -1

def path(node, group, lit):
    cls = node.get('class', '')
    if cls == 'iso-halo':
        return
    tone = node.get('data-tone', 'lo')
    if 'iso-line' in cls:
        code = {'faint': 0, 'lo': 1, 'mid': 2, 'hi': 3, 'lit': 4}.get(tone, 1)
        if lit and 'iso-edge' in cls:
            code = 4
        stroke = 1
    elif 'iso-shade' in cls:
        code = 11 if lit else 5 + int(node.get('data-shade', '0'))
        stroke = 0
    elif 'iso-top' in cls:
        code = 10 if lit else 9
        stroke = 0
    else:
        code = 12 if lit else 13
        stroke = 0
    tokens = re.findall(r'[MLZ]|-?\d+(?:\.\d+)?', node.get('d', ''))
    commands = []
    i = 0
    while i < len(tokens):
        command = tokens[i]
        if command not in ('M', 'L', 'Z'):
            raise ValueError('Unsupported SVG path command')
        i += 1
        if command == 'Z':
            commands.append((command,))
        else:
            commands.append((command, float(tokens[i]), float(tokens[i+1])))
            i += 2
    shapes.append((group, code, stroke, commands))

for node in svg:
    if node.tag.endswith('g'):
        group += 1
        for child in node:
            path(child, group, 'data-lit' in node.attrib)
    elif node.tag.endswith('path'):
        path(node, group, False)

with (root / 'display-engine.vec').open('wb') as out:
    out.write(b'LIVG')
    out.write(struct.pack('>HHH', 640, 400, len(shapes)))
    for group, code, stroke, commands in shapes:
        out.write(struct.pack('>hBBH', group, code, stroke, len(commands)))
        for cmd in commands:
            out.write(cmd[0].encode())
            if len(cmd) == 3:
                out.write(struct.pack('>ff', cmd[1], cmd[2]))
print('Compiled', len(shapes), 'SVG paths')
