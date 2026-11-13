#!/usr/bin/env python3
"""Compare public/protected DEX descriptors with the official CloudStream v4.5.4 APK.

No Android execution or third-party Python dependency. Includes Kotlin default-method
bridges and constructors. Baseline is extracted, never handwritten. Usage:
  python3 scripts/check-cloudstream-abi.py --reference official.apk --write-baseline baseline.json
  python3 scripts/check-cloudstream-abi.py --baseline baseline.json --candidate mutiny.apk
"""
import argparse
import hashlib
import json
import struct
import zipfile

PREFIX = 'Lcom/lagradost/cloudstream3/'
NAMES = ['MainAPI', 'MainAPIKt', 'APIHolder', 'SearchResponse', 'LoadResponse',
         'MovieSearchResponse', 'TvSeriesSearchResponse', 'AnimeSearchResponse',
         'MovieLoadResponse', 'TvSeriesLoadResponse', 'AnimeLoadResponse', 'Episode',
         'SubtitleFile', 'HomePageResponse', 'HomePageList', 'MainPageData', 'MainPageRequest',
         'plugins/BasePlugin', 'plugins/Plugin', 'utils/ExtractorApi', 'utils/ExtractorApiKt',
         'utils/ExtractorLink', 'utils/ExtractorLinkType', 'utils/ExtractorLinkPlayList',
         'actions/VideoClickAction', 'actions/VideoClickActionHolder', 'utils/UiText',
         'ui/result/ResultEpisode', 'ui/result/VideoWatchState', 'ui/result/LinkLoadingResult',
         'ui/player/SubtitleData', 'ui/player/SubtitleOrigin']
SELECTED = {PREFIX + name + ';' for name in NAMES}


def read_dex(data):
    def u32(offset): return struct.unpack_from('<I', data, offset)[0]
    def u16(offset): return struct.unpack_from('<H', data, offset)[0]
    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = data[offset]; offset += 1
            value |= (byte & 127) << shift
            if not byte & 128: return value, offset
        raise ValueError('Invalid ULEB128')
    strings = []
    for i in range(u32(56)):
        offset = u32(u32(60) + 4*i)
        _, offset = uleb(offset)
        strings.append(data[offset:data.index(0, offset)].decode('utf-8', errors='replace'))
    types = [strings[u32(u32(68) + 4*i)] for i in range(u32(64))]
    def typelist(offset):
        return [types[u16(offset+4+2*i)] for i in range(u32(offset))] if offset else []
    protos = []
    for i in range(u32(72)):
        offset = u32(76) + 12*i
        protos.append('(' + ''.join(typelist(u32(offset+8))) + ')' + types[u32(offset+4)])
    fields = []
    for i in range(u32(80)):
        offset = u32(84)+8*i
        fields.append(strings[u32(offset+4)] + ':' + types[u16(offset+2)])
    methods = []
    for i in range(u32(88)):
        offset = u32(92)+8*i
        methods.append(strings[u32(offset+4)] + protos[u16(offset+2)])
    result = {}
    for i in range(u32(96)):
        offset = u32(100)+32*i
        name = types[u32(offset)]
        # Named API nested types are stable. Lambda/state-machine classes are implementation
        # details whose names and captures change with compiler/host adaptation.
        api_nested = ('Companion', 'DefaultImpls', 'Manifest', 'DynamicString', 'StringResource')
        if name not in SELECTED and not any(name == base[:-1] + '$' + nested + ';'
                for base in SELECTED for nested in api_nested): continue
        parent = u32(offset+8)
        record = {'super': None if parent == 0xffffffff else types[parent],
                  'interfaces': sorted(typelist(u32(offset+12))), 'fields': [], 'methods': []}
        cursor = u32(offset+24)
        if cursor:
            counts = []
            for _ in range(4):
                count, cursor = uleb(cursor); counts.append(count)
            for category, count in enumerate(counts):
                index = 0
                for _ in range(count):
                    delta, cursor = uleb(cursor); index += delta
                    access, cursor = uleb(cursor)
                    if category >= 2: _, cursor = uleb(cursor)
                    if access & 5 and not (category >= 2 and methods[index].startswith('$r8$')):  # public/protected API; exclude post-compile optimizer helpers
                        record['methods' if category >= 2 else 'fields'].append((methods if category >= 2 else fields)[index])
        record['fields'].sort(); record['methods'].sort()
        result[name] = record
    return result


def read_apk(path):
    result = {}
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                result.update(read_dex(archive.read(name)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference'); parser.add_argument('--write-baseline')
    parser.add_argument('--baseline'); parser.add_argument('--candidate')
    args = parser.parse_args()
    if args.reference:
        baseline = {'upstream': 'recloudstream/cloudstream v4.5.4',
                    'apk_sha256': hashlib.sha256(open(args.reference, 'rb').read()).hexdigest(),
                    'classes': read_apk(args.reference)}
        missing = SELECTED - baseline['classes'].keys()
        if missing: raise SystemExit('Reference lacks classes: ' + str(missing))
        if args.write_baseline:
            with open(args.write_baseline, 'w') as out: json.dump(baseline, out, indent=2, sort_keys=True); out.write('\n')
    else:
        with open(args.baseline) as source: baseline = json.load(source)
    if args.candidate:
        candidate = read_apk(args.candidate)
        errors = []
        for name, expected in baseline['classes'].items():
            found = candidate.get(name)
            if not found: errors.append(name + ': missing class'); continue
            for key in ('super', 'interfaces'):
                if found[key] != expected[key]: errors.append(name + ': changed ' + key)
            for key in ('fields', 'methods'):
                for signature in set(expected[key]) - set(found[key]): errors.append(name + ': missing ' + signature)
        if errors:
            print('\n'.join(sorted(errors))); raise SystemExit(1)
        print(f"PASS: {len(baseline['classes'])} classes preserve upstream public/protected descriptors and inheritance")


if __name__ == '__main__': main()
