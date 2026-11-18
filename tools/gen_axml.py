#!/usr/bin/env python3
"""把可读的 AndroidManifest.xml 转成系统能解析的二进制 AXML。

不依赖 aapt2，因此 arm64 设备上也能本地构建模块 APK。
支持：元素树、android: 命名空间属性（按固定资源 ID 编码）、字符串 / int / bool 值。
"""
import struct
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# android.R.attr 资源 ID + 值类型（平台 ID，跨版本稳定）
ATTR = {
    "theme": (0x01010000, "string"),
    "label": (0x01010001, "string"),
    "icon": (0x01010002, "string"),
    "name": (0x01010003, "string"),
    "permission": (0x01010006, "string"),
    "debuggable": (0x0101000F, "bool"),
    "exported": (0x01010010, "bool"),
    "value": (0x01010024, "string"),
    "resource": (0x01010025, "string"),
    "minSdkVersion": (0x0101020C, "int"),
    "versionCode": (0x0101021B, "int"),
    "versionName": (0x0101021C, "string"),
    "targetSdkVersion": (0x01010270, "int"),
    "allowBackup": (0x01010280, "bool"),
    "supportsRtl": (0x010103AF, "bool"),
    "extractNativeLibs": (0x010104EA, "bool"),
}

TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_BOOLEAN = 0x12

NO_ENTRY = 0xFFFFFFFF


class Pool:
    def __init__(self):
        self.strings = []
        self.resmap = []
        self.idx = {}

    def add(self, s, resid=0):
        if s in self.idx:
            i = self.idx[s]
            if resid and not self.resmap[i]:
                self.resmap[i] = resid
            return i
        i = len(self.strings)
        self.idx[s] = i
        self.strings.append(s)
        self.resmap.append(resid)
        return i

    def data(self):
        offsets = []
        blob = bytearray()
        for s in self.strings:
            offsets.append(len(blob))
            blob += struct.pack("<H", len(s))
            blob += s.encode("utf-16-le")
            blob += b"\x00\x00"
        while len(blob) % 4:
            blob += b"\x00"
        header_size = 28
        strings_start = header_size + 4 * len(self.strings)
        size = strings_start + len(blob)
        out = struct.pack("<HHIIIIII", 0x0001, header_size, size,
                          len(self.strings), 0, 0, strings_start, 0)
        out += b"".join(struct.pack("<I", o) for o in offsets)
        out += bytes(blob)
        return out

    def resmap_chunk(self):
        ids = list(self.resmap)
        while ids and ids[-1] == 0:
            ids.pop()
        size = 8 + 4 * len(ids)
        out = struct.pack("<HHI", 0x0180, 8, size)
        out += b"".join(struct.pack("<I", i) for i in ids)
        return out


def strip_ns(tag):
    return tag.split("}", 1)[1] if "}" in tag else tag


def attr_key(k):
    if k.startswith("{"):
        ns, name = k[1:].split("}", 1)
        return ("android" if ns == ANDROID_NS else ns), name
    return None, k


def collect(node, pool):
    pool.add(strip_ns(node.tag))
    for k, v in node.attrib.items():
        ns, name = attr_key(k)
        if ns == "android":
            resid, _ = ATTR.get(name, (0, "string"))
            pool.add(name, resid)
        else:
            pool.add(name)
        pool.add(v)
    for c in list(node):
        collect(c, pool)


def node_chunk(node, pool):
    out = bytearray()
    name_idx = pool.idx[strip_ns(node.tag)]
    attrs = []
    for k, v in node.attrib.items():
        ns, name = attr_key(k)
        if ns == "android":
            resid, typ = ATTR.get(name, (0, "string"))
            ans = pool.idx[ANDROID_NS]
        else:
            resid, typ = 0, "string"
            ans = NO_ENTRY
        aname = pool.idx[name]
        v_idx = pool.idx[v]
        if typ == "bool":
            data = 0xFFFFFFFF if v.lower() in ("true", "1") else 0
            attrs.append((ans, aname, NO_ENTRY, TYPE_INT_BOOLEAN, data))
        elif typ == "int":
            attrs.append((ans, aname, NO_ENTRY, TYPE_INT_DEC, int(v)))
        else:
            attrs.append((ans, aname, v_idx, TYPE_STRING, v_idx))

    body = struct.pack("<IIHHHHHH", NO_ENTRY, name_idx, 20, 20, len(attrs), 0, 0, 0)
    for (ans, aname, raw, typ, data) in attrs:
        body += struct.pack("<III", ans, aname, raw)
        body += struct.pack("<HBBI", 8, 0, typ, data)
    size = 16 + len(body)
    out += struct.pack("<HHIII", 0x0102, 16, size, 1, NO_ENTRY)
    out += body
    return bytes(out)


def end_chunk(node, pool):
    size = 24
    return struct.pack("<HHIII", 0x0103, 16, size, 1, NO_ENTRY) + \
        struct.pack("<II", NO_ENTRY, pool.idx[strip_ns(node.tag)])


def emit(node, pool, out):
    out += node_chunk(node, pool)
    for c in list(node):
        emit(c, pool, out)
    out += end_chunk(node, pool)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    tree = ET.parse(src)
    root = tree.getroot()

    pool = Pool()
    pool.add("android")
    pool.add(ANDROID_NS)
    collect(root, pool)

    # start/end namespace：header(16) + prefix(4) + uri(4)
    # header(8) + lineNumber(4) + comment(4) + prefix(4) + uri(4) = 24
    ns_start = struct.pack("<HHIIIII", 0x0100, 16, 24, 1, NO_ENTRY,
                           pool.idx["android"], pool.idx[ANDROID_NS])
    ns_end = struct.pack("<HHIIIII", 0x0101, 16, 24, 1, NO_ENTRY,
                         pool.idx["android"], pool.idx[ANDROID_NS])

    body = bytearray()
    emit(root, pool, body)

    string_pool = pool.data()
    resmap = pool.resmap_chunk()
    payload = ns_start + bytes(body) + ns_end
    total = 8 + len(string_pool) + len(resmap) + len(payload)
    out = struct.pack("<HHI", 0x0003, 8, total)
    out += string_pool + resmap + payload

    with open(dst, "wb") as f:
        f.write(out)
    print("AXML -> %s (%d bytes, %d strings)" % (dst, len(out), len(pool.strings)))


if __name__ == "__main__":
    main()
