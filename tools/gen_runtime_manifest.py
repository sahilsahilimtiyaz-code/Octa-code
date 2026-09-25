#!/usr/bin/env python3
"""Generate app/src/main/assets/runtime-manifest.json from the Termux apt index.

Why this exists (M5/R1):
  The app downloads a coding runtime (bash, node, python, proot, git, ...).
  Every artifact is PINNED by URL + SHA-256 so the app can never execute a
  byte stream it did not verify. This script is the single, reviewable place
  where those pins come from — re-run it to refresh versions.

Usage:
  python3 tools/gen_runtime_manifest.py            # write the asset
  python3 tools/gen_runtime_manifest.py --check    # fail if asset is stale

Design notes:
  * Dependency closures are computed ONCE here and frozen into the asset, so
    the app needs no apt solver at runtime (see README "Honesty principle").
  * Groups are independent install units. A group lists its FULL closure so it
    can be installed standalone; the app dedupes against what is already
    downloaded, so the number shown in the UI is the real download cost.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from datetime import date

INDEX_URL = (
    "https://packages.termux.dev/apt/termux-main/"
    "dists/stable/main/binary-aarch64/Packages"
)
BASE_URL = "https://packages.termux.dev/apt/termux-main/"
ARCH = "aarch64"

# id, title, description, seeds, optional
GROUPS = [
    ("base", "Linux userland",
     "bash, coreutils, tar, find, sed + Termux core",
     ["bash", "coreutils", "tar", "gzip", "xz-utils", "findutils", "grep", "sed",
      "gawk", "procps", "util-linux", "less", "dash", "diffutils", "psmisc",
      "dialog", "attr", "termux-tools", "termux-exec", "termux-core",
      "termux-am", "termux-am-socket", "ca-certificates", "resolv-conf"],
     False),
    ("proot", "PRoot sandbox",
     "Fake-root executor — runs the full userland without root",
     ["proot"], False),
    ("node", "Node.js 24",
     "Required by OpenCode / Claude Code / Codex CLIs",
     ["nodejs-lts"], False),
    ("python", "Python 3.14",
     "pip + pytest — unblocks mission analyze/test phases",
     ["python"], False),
    ("devtools", "Dev tools",
     "git, ripgrep, fd, fzf, curl",
     ["git", "ripgrep", "fd", "fzf", "curl"], False),
    ("rust", "Rust toolchain",
     "rustc, cargo, clang/llvm + NDK sysroot — part of the default install",
     ["rust"], False),
]


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "octa-manifest/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", "replace")


def parse_index(text: str) -> dict[str, dict[str, str]]:
    """RFC822-ish apt Packages index -> {package: fields}."""
    records: dict[str, dict[str, str]] = {}
    for para in text.split("\n\n"):
        if not para.strip():
            continue
        fields: dict[str, str] = {}
        key = None
        for line in para.split("\n"):
            if line[:1].isspace() and key:
                fields[key] += " " + line.strip()
            elif ":" in line:
                key, _, value = line.partition(":")
                fields[key] = value.strip()
        if "Package" in fields:
            records[fields["Package"]] = fields
    return records


def depends_of(fields: dict[str, str]) -> list[str]:
    """Direct dependency names: strip version constraints and alternatives."""
    out = []
    for part in (fields.get("Depends") or "").split(","):
        part = part.split("|")[0]
        name = part.split("(")[0].split("[")[0].strip().split(" ")[0]
        if name:
            out.append(name)
    return out


def closure(seeds: list[str], records: dict[str, dict[str, str]]) -> list[str]:
    seen: set[str] = set()
    order: list[str] = []
    queue = list(seeds)
    missing = []
    while queue:
        name = queue.pop(0)
        if name in seen:
            continue
        if name not in records:
            missing.append(name)
            continue
        seen.add(name)
        order.append(name)
        queue.extend(depends_of(records[name]))
    if missing:
        raise SystemExit(f"missing packages in index: {sorted(set(missing))}")
    return order


def build() -> dict:
    print(f"fetching {INDEX_URL}", file=sys.stderr)
    records = parse_index(fetch(INDEX_URL))

    groups = []
    for gid, title, desc, seeds, optional in GROUPS:
        members = closure(seeds, records)
        items = []
        for name in members:
            f = records[name]
            provides = [
                p.split("(")[0].strip()
                for p in (f.get("Provides") or "").split(",")
                if p.strip()
            ]
            items.append({
                "id": name,
                "version": f.get("Version", ""),
                "size": int(f.get("Size", "0")),
                "sha256": f.get("SHA256", ""),
                "path": f.get("Filename", ""),
                "provides": provides,
            })
        groups.append({
            "id": gid,
            "title": title,
            "description": desc,
            "optional": optional,
            "totalBytes": sum(i["size"] for i in items),
            "items": items,
        })
        print(f"  {gid:9s} {len(items):3d} pkgs {sum(i['size'] for i in items)/1e6:8.1f} MB",
              file=sys.stderr)

    union = {i["id"]: i for g in groups for i in g["items"]}
    return {
        "schema": 1,
        "name": "octa-runtime",
        "arch": ARCH,
        "abi": "arm64-v8a",
        "index": INDEX_URL,
        "baseUrl": BASE_URL,
        "groups": groups,
        "unionBytes": sum(i["size"] for i in union.values()),
        "packageCount": len(union),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true",
                        help="verify the committed asset matches a fresh build")
    args = parser.parse_args()

    payload = build()

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "app", "src", "main", "assets", "runtime-manifest.json")

    existing = None
    if os.path.exists(out):
        with open(out, encoding="utf-8") as fh:
            try:
                existing = json.load(fh)
            except ValueError:
                existing = None

    # `generated` is the date the PINS were produced, not today's date — otherwise
    # --check would report a false diff every midnight with identical pins.
    same_pins = existing is not None and {
        k: v for k, v in existing.items() if k != "generated"
    } == {k: v for k, v in payload.items()}
    generated = existing["generated"] if same_pins else date.today().isoformat()

    # Fixed key order (generated sits before groups, matching the committed shape).
    final = {}
    for key in ("schema", "name", "arch", "abi", "index", "baseUrl"):
        final[key] = payload[key]
    final["generated"] = generated
    for key in ("groups", "unionBytes", "packageCount"):
        final[key] = payload[key]

    text = json.dumps(final, indent=2, sort_keys=False) + "\n"

    if args.check:
        current = open(out, encoding="utf-8").read() if os.path.exists(out) else ""
        if current != text:
            print("runtime-manifest.json is STALE — re-run without --check", file=sys.stderr)
            return 1
        print("runtime-manifest.json is up to date", file=sys.stderr)
        return 0

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(f"wrote {out}", file=sys.stderr)
    print(f"union: {final['packageCount']} packages, "
          f"{final['unionBytes']/1e6:.1f} MB", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
