#!/usr/bin/env python3
"""Compare the public/protected provider ABI of two Android APKs directly from DEX."""

import argparse
import hashlib
import re
import struct
import zipfile
from dataclasses import dataclass

REFERENCE_SHA256 = "b3c43a34240ea7ddd65358368bfd6876aa4bde164948a28378c280d66e4414aa"
ACCESS = 0x0005  # public or protected
CLASS_FLAGS = 0x0611  # public, final, interface, abstract
MEMBER_FLAGS = 0x041F  # visibility, static, final, abstract

CORE_TYPES = re.compile(
    r"Lcom/lagradost/(?:api/(?:Log|ContextHelper_androidKt)"
    r"|nicehttp/(?:NiceFile|NiceResponse|Requests|RequestsKt|ResponseParser)"
    r"|cloudstream3/(?:"
    r"(?:MainAPI(?:Kt)?|MainActivityKt|APIHolder|SearchResponse|[A-Za-z]+SearchResponse|"
    r"LoadResponse|[A-Za-z]+LoadResponse|Episode(?:Response)?|TvType|DubStatus|Score|"
    r"MainPage(?:Data|Request)|HomePage(?:List|Response)|SubtitleFile|Actor(?:Data|Role)?|"
    r"SeasonData|NextAiring|TrailerData|ShowStatus|IDownloadableMinimum|ErrorLoadingException|"
    r"VPNStatus|ProviderType|AnimeSearchResponse|ProvidersInfoJson|SettingsJson)(?:\$(?:Companion|DefaultImpls))?"
    r"|plugins/(?:BasePlugin(?:Kt|\$Manifest)?|Plugin|CloudstreamPlugin)"
    r"|network/WebViewResolver(?:_androidKt|\$Companion)?"
    r"|utils/(?:[A-Za-z]*ExtractorLink(?:Type|PlayList)?|ExtractorApi(?:Kt)?|Qualities|"
    r"AppUtils(?:\$Companion)?|Coroutines(?:Kt|_androidKt)?|SubtitleHelper|"
    r"JsUnpacker|JsHunter|M3u8Helper(?:\$Companion)?|StringUtilsKt|UiText(?:\$[A-Za-z]+)?|TextUtilKt)"
    r"|extractors/helper/(?:AesHelper|CryptoJS|CryptoJSHelperKt)"
    r"|ParCollectionsKt|mvvm/(?:ArchComponentExtKt|DebugException)"
    r"|actions/VideoClickAction"
    r"|ui/result/(?:ResultEpisode|VideoWatchState|LinkLoadingResult)"
    r"|ui/player/(?:SubtitleData(?:\$Companion)?|SubtitleOrigin)"
    r"));"
)


@dataclass
class ClassAbi:
    flags: int
    parent: str
    interfaces: set
    fields: dict
    methods: dict


class Dex:
    def __init__(self, data):
        if not data.startswith(b"dex\n"):
            raise ValueError("Unrecognized DEX file")
        self.data = data
        self.strings = {}
        self.types = {}
        self.protos = {}

    def u16(self, offset):
        return struct.unpack_from("<H", self.data, offset)[0]

    def u32(self, offset):
        return struct.unpack_from("<I", self.data, offset)[0]

    def uleb(self, offset):
        value = 0
        for shift in range(0, 35, 7):
            byte = self.data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
        raise ValueError("Invalid DEX ULEB128")

    def string(self, index):
        if index not in self.strings:
            pointer = self.u32(self.u32(60) + 4 * index)
            _, start = self.uleb(pointer)
            end = self.data.index(0, start)
            self.strings[index] = self.data[start:end].decode("utf-8", errors="replace")
        return self.strings[index]

    def type(self, index):
        if index == 0xFFFFFFFF:
            return ""
        if index not in self.types:
            self.types[index] = self.string(self.u32(self.u32(68) + 4 * index))
        return self.types[index]

    def type_list(self, pointer):
        if not pointer:
            return []
        return [self.type(self.u16(pointer + 4 + 2 * i)) for i in range(self.u32(pointer))]

    def proto(self, index):
        if index not in self.protos:
            pointer = self.u32(76) + 12 * index
            parameters = self.type_list(self.u32(pointer + 8))
            self.protos[index] = "(" + "".join(parameters) + ")" + self.type(self.u32(pointer + 4))
        return self.protos[index]

    def field(self, index):
        pointer = self.u32(84) + 8 * index
        return self.string(self.u32(pointer + 4)) + ":" + self.type(self.u16(pointer + 2))

    def method(self, index):
        pointer = self.u32(92) + 8 * index
        return self.string(self.u32(pointer + 4)) + self.proto(self.u16(pointer + 2))

    def members(self, pointer):
        fields = {}
        methods = {}
        if not pointer:
            return fields, methods
        counts = []
        for _ in range(4):
            count, pointer = self.uleb(pointer)
            counts.append(count)
        for section, count in enumerate(counts):
            index = 0
            for _ in range(count):
                delta, pointer = self.uleb(pointer)
                flags, pointer = self.uleb(pointer)
                index += delta
                if section < 2:
                    if flags & ACCESS:
                        fields[self.field(index)] = flags & MEMBER_FLAGS
                else:
                    _, pointer = self.uleb(pointer)
                    if flags & ACCESS:
                        signature = self.method(index)
                        # D8/R8 names private lambda implementation bridges per build.
                        if not signature.startswith("$r8$lambda$"):
                            methods[signature] = flags & MEMBER_FLAGS
        return fields, methods

    def classes(self, selected):
        for i in range(self.u32(96)):
            pointer = self.u32(100) + 32 * i
            descriptor = self.type(self.u32(pointer))
            if not selected(descriptor):
                continue
            fields, methods = self.members(self.u32(pointer + 24))
            yield descriptor, ClassAbi(
                self.u32(pointer + 4) & CLASS_FLAGS,
                self.type(self.u32(pointer + 8)),
                set(self.type_list(self.u32(pointer + 12))),
                fields,
                methods,
            )


def read_classes(apk, selected):
    classes = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                classes.update(Dex(archive.read(name)).classes(selected))
    return classes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", help="Official CloudStream v4.5.4 release APK")
    parser.add_argument("candidate", help="Mutiny Media debug APK")
    args = parser.parse_args()
    with open(args.reference, "rb") as reference_file:
        if hashlib.file_digest(reference_file, "sha256").hexdigest() != REFERENCE_SHA256:
            parser.error("Reference APK SHA-256 does not match the pinned CloudStream release")
    reference = read_classes(args.reference, CORE_TYPES.fullmatch)
    candidate = read_classes(args.candidate, reference.__contains__)
    if len(reference) < 90:
        parser.error(f"Expected at least 90 provider ABI classes, found {len(reference)}")

    failures = []
    for descriptor, expected in sorted(reference.items()):
        actual = candidate.get(descriptor)
        if actual is None:
            failures.append(f"{descriptor} missing class")
            continue
        if (expected.flags != actual.flags or expected.parent != actual.parent
                or not expected.interfaces.issubset(actual.interfaces)):
            failures.append(f"{descriptor} changed class flags, superclass or interfaces")
        for members, found in ((expected.fields, actual.fields), (expected.methods, actual.methods)):
            for signature, flags in sorted(members.items()):
                if found.get(signature) != flags:
                    failures.append(f"{descriptor} missing or changed {signature}")

    print(f"Compared {len(reference)} classes, {sum(len(c.fields) for c in reference.values())} fields "
          f"and {sum(len(c.methods) for c in reference.values())} methods against CloudStream v4.5.4")
    for failure in failures:
        print(failure)
    if failures:
        print(f"ABI differences: {len(failures)}")
        return 1
    print("Provider ABI check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
