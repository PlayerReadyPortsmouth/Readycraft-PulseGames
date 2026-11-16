#!/usr/bin/env python3
"""Independently parse exported Sponge v3 artifacts; no Minecraft or third-party NBT dependency."""
import csv
import gzip
import io
import json
from pathlib import Path
import struct
import sys


def nbt(data):
    source = io.BytesIO(data)
    def number(fmt):
        size = struct.calcsize(fmt)
        raw = source.read(size)
        if len(raw) != size:
            raise ValueError("Truncated NBT")
        return struct.unpack(fmt, raw)[0]
    def string():
        return source.read(number('>H')).decode('utf-8')
    def body(kind):
        if kind in {1, 2, 3, 4, 5, 6}:
            return number({1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[kind])
        if kind == 7:
            count = number('>i')
            if count < 0:
                raise ValueError("Negative byte array")
            value = source.read(count)
            if len(value) != count:
                raise ValueError("Truncated byte array")
            return value
        if kind == 8:
            return string()
        if kind == 9:
            item, count = number('>b'), number('>i')
            if count < 0:
                raise ValueError("Negative list")
            return [body(item) for _ in range(count)]
        if kind == 10:
            value = {}
            while (item := number('>b')) != 0:
                name = string()
                if name in value:
                    raise ValueError("Duplicate NBT key")
                value[name] = body(item)
            return value
        if kind in {11, 12}:
            count = number('>i')
            if count < 0:
                raise ValueError("Negative array")
            return [number('>i' if kind == 11 else '>q') for _ in range(count)]
        raise ValueError(f"Unexpected tag {kind}")
    assert number('>b') == 10
    string()
    result = body(10)
    assert source.read() == b'', "Trailing data after root"
    return result


def check(directory):
    for name, expected in [('pulse_present_panic_test',88), ('pulse_grinch_hunt_test',63)]:
        with gzip.open(directory / (name+'.schem'), 'rb') as source:
            schema = nbt(source.read())['Schematic']
        assert schema['Version'] == 3 and schema['DataVersion'] == 3955
        width, height, length = (schema[key] for key in ('Width','Height','Length'))
        assert (width,height,length) == (93,49,93)
        assert schema['Offset'] == [-46,64,-46]
        blocks = schema['Blocks']
        # All palette indices fit a single varint byte in these small maps.
        assert len(blocks['Data']) == width*height*length
        assert set(blocks['Data']) <= set(blocks['Palette'].values())
        entities = blocks['BlockEntities']
        assert len(entities) == expected
        with (directory / (name+'-markers.csv')).open() as source:
            markers = list(csv.DictReader(source))
        assert len(markers) == expected
        positions = set()
        for entity, marker in zip(entities, markers):
            assert entity['Id'] == 'minecraft:sign'
            texts = [json.loads(message)['text'] for message in entity['Data']['front_text']['messages']]
            assert texts[:3] == ['[pulse]',marker['key'],marker['facing']]
            position = [int(marker[key])-offset for key,offset in zip(('x','y','z'), schema['Offset'])]
            assert entity['Pos'] == position
            assert tuple(position) not in positions
            positions.add(tuple(position))
            x,y,z = position
            index = x+z*width+y*width*length
            assert blocks['Data'][index] == blocks['Palette']['minecraft:oak_sign[rotation=0,waterlogged=false]']
            assert blocks['Data'][index-width*length] != 0, 'Floating marker without support'
        print(f'{name}: valid NBT, {len(entities)} exact signs, unique positions, all supported')


if __name__ == '__main__':
    check(Path(sys.argv[1] if len(sys.argv)>1 else 'build/christmas-test-maps'))
