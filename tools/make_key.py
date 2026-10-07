#!/usr/bin/env python3
"""Creates the signing key (keep it! updates must be signed with the same key)."""
import datetime
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Netak Bazman"),
                  x509.NameAttribute(NameOID.COUNTRY_NAME, "IL")])
now = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
        .public_key(key.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(now).not_valid_after(now + datetime.timedelta(days=365 * 30))
        .sign(key, hashes.SHA256()))
open(sys.argv[1], "wb").write(key.private_bytes(serialization.Encoding.PEM,
                                                serialization.PrivateFormat.PKCS8,
                                                serialization.NoEncryption()))
open(sys.argv[2], "wb").write(cert.public_bytes(serialization.Encoding.DER))
print("key created")
