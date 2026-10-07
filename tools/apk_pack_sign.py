#!/usr/bin/env python3
"""Packs aapt2 output + classes.dex into an aligned APK and signs it with APK Signature Scheme v2.

usage: apk_pack_sign.py base.apk classes.dex key.pem cert.der out.apk
"""
import hashlib
import struct
import sys
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


def pack(base_apk, dex, out):
    src = zipfile.ZipFile(base_apk)
    with zipfile.ZipFile(out, "w") as z:
        entries = [(i, src.read(i.filename)) for i in src.infolist()]
        entries.append((None, open(dex, "rb").read()))
        for info, data in entries:
            if info is None:
                zi = zipfile.ZipInfo("classes.dex", date_time=(2008, 1, 1, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_DEFLATED
            else:
                zi = zipfile.ZipInfo(info.filename, date_time=(2008, 1, 1, 0, 0, 0))
                zi.compress_type = info.compress_type
            zi.external_attr = 0
            zi.create_system = 0
            if zi.compress_type == zipfile.ZIP_STORED:
                # 4-byte align the data of uncompressed entries (resources.arsc must be aligned)
                offset = z.fp.tell()
                name_len = len(zi.filename.encode("utf-8"))
                data_start = offset + 30 + name_len
                pad = (4 - data_start % 4) % 4
                zi.extra = b"\x00" * pad
            z.writestr(zi, data)
    # verify alignment
    with open(out, "rb") as f:
        raw = f.read()
    for info in zipfile.ZipFile(out).infolist():
        if info.compress_type == zipfile.ZIP_STORED:
            h = info.header_offset
            n, e = struct.unpack("<HH", raw[h + 26:h + 30])
            assert (h + 30 + n + e) % 4 == 0, info.filename


def lp(b):
    return struct.pack("<I", len(b)) + b


def chunk_digest(sections):
    chunks = []
    for sec in sections:
        for i in range(0, len(sec), 1 << 20):
            c = sec[i:i + (1 << 20)]
            chunks.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(chunks)) + b"".join(chunks)).digest()


def find_eocd(data):
    i = data.rfind(b"PK\x05\x06")
    assert i >= 0
    cd_size, cd_off = struct.unpack("<II", data[i + 12:i + 20])
    return i, cd_off, cd_size


def sign_v2(apk, key_pem, cert_der, out):
    data = open(apk, "rb").read()
    eocd_off, cd_off, cd_size = find_eocd(data)
    assert cd_off + cd_size == eocd_off
    contents = data[:cd_off]
    cd = data[cd_off:eocd_off]
    eocd = data[eocd_off:]

    key = serialization.load_pem_private_key(open(key_pem, "rb").read(), None)
    cert = open(cert_der, "rb").read()
    pub = key.public_key().public_bytes(serialization.Encoding.DER,
                                        serialization.PublicFormat.SubjectPublicKeyInfo)
    ALG = 0x0103  # RSASSA-PKCS1-v1_5 with SHA2-256

    digest = chunk_digest([contents, cd, eocd])
    digests = lp(struct.pack("<I", ALG) + lp(digest))
    certs = lp(cert)
    signed_data = lp(digests) + lp(certs) + lp(b"")
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures = lp(struct.pack("<I", ALG) + lp(sig))
    signer = lp(signed_data) + lp(signatures) + lp(pub)
    v2_value = lp(lp(signer))

    pair = struct.pack("<I", 0x7109871A) + v2_value
    pairs = struct.pack("<Q", len(pair)) + pair
    size = len(pairs) + 8 + 16
    block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"

    new_eocd = bytearray(eocd)
    struct.pack_into("<I", new_eocd, 16, cd_off + len(block))
    with open(out, "wb") as f:
        f.write(contents + block + cd + bytes(new_eocd))


if __name__ == "__main__":
    base, dex, key, cert, out = sys.argv[1:6]
    tmp = out + ".unsigned"
    pack(base, dex, tmp)
    sign_v2(tmp, key, cert, out)
    print("signed:", out)
