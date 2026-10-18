"""Check 64-bit LOAD alignment and the DataStore RELRO regression in an APK/AAB.

Usage: python tools/verify_native_libraries.py path/to/app.apk
Static checks do not replace running the app on a 16 KB device/emulator.
DataStore upstream linker fix: https://android.googlesource.com/platform/frameworks/support/+/608ce7d213f80094fa3ed9b59debad0d4af65d41
"""

import argparse
from pathlib import Path
import struct
import sys
import zipfile

PAGE = 16_384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def check_elf(data, require_16k_relro=False):
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a little-endian 64-bit ELF")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError("Invalid ELF program-header table")
    problems = []
    load_count = 0
    relro_ends = []
    for index in range(count):
        kind, _, file_offset, address, _, _, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + size * index
        )
        if kind == PT_LOAD:
            load_count += 1
            if alignment < PAGE or address % PAGE != file_offset % PAGE:
                problems.append(f"LOAD segment {index} is not 16 KB aligned")
        elif kind == PT_GNU_RELRO:
            end = address + memory_size
            relro_ends.append(end)
            # Specifically guard the DataStore binary reported by Google Play.
            # Other linkers/libraries can legitimately use a smaller RELRO end;
            # this is not a universal requirement for all native libraries.
            if require_16k_relro and end % PAGE:
                problems.append(f"DataStore GNU_RELRO ends at {end:#x}, outside a 16 KB boundary")
    if not load_count:
        problems.append("No LOAD segments")
    return problems, load_count, relro_ends


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    args = parser.parse_args()
    checked = 0
    failed = False
    with zipfile.ZipFile(args.artifact) as archive:
        for name in archive.namelist():
            if not name.startswith(("base/lib/", "lib/")) or not name.endswith(".so"):
                continue
            if not any(f"/{abi}/" in name for abi in ("arm64-v8a", "x86_64")):
                continue
            checked += 1
            problems, loads, ends = check_elf(archive.read(name),
                require_16k_relro=name.endswith("/libdatastore_shared_counter.so"))
            if problems:
                failed = True
                print(f"FAIL {name}: {'; '.join(problems)}")
            else:
                print(f"PASS {name}: {loads} LOAD segments; RELRO ends {[hex(end) for end in ends]}")
    print(f"Checked {checked} 64-bit native libraries in {args.artifact.name}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
