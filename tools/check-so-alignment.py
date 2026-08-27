#!/usr/bin/env python3
"""
Check that every arm64-v8a .so in the built APK is 16KB-page-aligned.

Android 15+ requires every PT_LOAD segment's p_align to be >= 0x4000
and every segment's p_offset to be a multiple of p_align. If a .so
fails this, the OS shows a page-size warning on every launch and
refuses to load the .so on 16KB-page-size devices (Pixel 8+, some
Samsung, etc).

Usage:
    tools/check-so-alignment.py app/build/outputs/apk/debug/app-debug.apk

Exit code:
    0 — every .so passes
    1 — one or more .so files have segments with p_align < 0x4000
    2 — usage error (file not found, no .so, etc)

Why this exists:
    The v0.70.0 release APK ships 5 arm64-v8a .so files, all
    with at least one PT_LOAD segment at p_align < 0x4000:
      - libmindanchor_llama.so          (vendored llama.cpp)
      - libandroidx.graphics.path.so    (Jetpack Graphics)
      - libsurface_util_jni.so          (ML Kit / CameraX)
      - libimage_processing_util_jni.so (ML Kit / CameraX)
      - libdatastore_shared_counter.so  (Jetpack DataStore counter)
    The fix is a post-link re-alignment pass (see
    app/build.gradle.kts :app:alignNativeLibsFor16KbPageSize).
"""
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

REQUIRED_ALIGN = 0x4000
PT_LOAD = 1

def check_so(path: Path) -> list[tuple[str, str, str, str]]:
    """Return list of bad (segment_index, p_type, p_align, p_offset) tuples."""
    bad = []
    data = path.read_bytes()
    if data[:4] != b"\x7fELF":
        return [("header", "?", "not ELF", "")]
    if data[4] != 2:  # 64-bit
        return [("header", "?", "not 64-bit", "")]
    phoff = struct.unpack("<Q", data[32:40])[0]
    phentsize = struct.unpack("<H", data[54:56])[0]
    phnum = struct.unpack("<H", data[56:58])[0]
    for i in range(phnum):
        base = phoff + i * phentsize
        p_type = struct.unpack("<I", data[base:base+4])[0]
        if p_type != PT_LOAD:
            continue
        p_offset = struct.unpack("<Q", data[base+8:base+16])[0]
        p_vaddr = struct.unpack("<Q", data[base+16:base+24])[0]
        p_align = struct.unpack("<Q", data[base+40:base+48])[0]
        if p_align < REQUIRED_ALIGN or (p_offset & (p_align - 1)) != 0:
            bad.append((
                f"#{i}",
                f"PT_LOAD",
                hex(p_align),
                f"offset={hex(p_offset)} vaddr={hex(p_vaddr)}",
            ))
    return bad

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check-so-alignment.py <path-to-apk>", file=sys.stderr)
        return 2
    apk_path = Path(sys.argv[1])
    if not apk_path.is_file():
        print(f"not a file: {apk_path}", file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        with zipfile.ZipFile(apk_path) as zf:
            zf.extractall(tmp)
        lib_dir = tmp / "lib" / "arm64-v8a"
        if not lib_dir.is_dir():
            print(f"no arm64-v8a in {apk_path}", file=sys.stderr)
            return 2
        any_bad = False
        for so in sorted(lib_dir.glob("*.so")):
            bad = check_so(so)
            if bad:
                any_bad = True
                first = bad[0]
                # Collapse duplicate p_aligns in the report — there's
                # no value in listing the same p_align twice; the
                # count matters, not the per-segment listing. The
                # tuples in `bad` are (idx, type, p_align_str,
                # offset_str), so strip the hex prefix when sorting.
                def _align_int(s):
                    return int(s, 16) if isinstance(s, str) and s.startswith("0x") else int(s)
                unique = sorted({_align_int(t[2]) for t in bad})
                print(
                    f"  {so.name:50s} ❌ {len(bad)} bad segments "
                    f"(unique p_aligns: {[hex(a) for a in unique]}, first: {first[0]} {first[1]} align={first[2]} {first[3]})"
                )
            else:
                print(f"  {so.name:50s} ✓ 16KB-aligned")
        if any_bad:
            print(
                "\nAt least one .so has p_align < 0x4000. "
                "Android 15+ will refuse to load it on 16KB-page devices "
                "and show a page-size warning on every launch."
            )
            return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
