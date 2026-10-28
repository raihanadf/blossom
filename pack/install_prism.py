#!/usr/bin/env python3
# installs or updates the "Blossom" prism launcher instance on this mac and on wyvern from build/blossom-<version>.mrpack.
# run ./gradlew build and pack/build_mrpack.py first.
# mods and the loader are replaced every time; worlds, options and configs changed in game are kept.
import hashlib
import json
import subprocess
import urllib.request
import zipfile
from pathlib import Path

# prism data folders, a "host:" prefix means over ssh
PRISMS = [
    str(Path.home() / "Library/Application Support/PrismLauncher"),
    "wyvern:/mnt/c/Users/raihan/AppData/Roaming/PrismLauncher",
]

root = Path(__file__).resolve().parent.parent
props = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
mrpack = root / "build" / f"blossom-{props['version']}.mrpack"
if not mrpack.exists():
    raise SystemExit(f"{mrpack} not found, run pack/build_mrpack.py first")

stage = root / "build" / "prism" / "Blossom"
cache = root / "build" / "prism" / "cache"
mods = stage / "minecraft" / "mods"
subprocess.run(["rm", "-rf", str(stage)], check=True)
mods.mkdir(parents=True)
cache.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(mrpack) as pack:
    index = json.loads(pack.read("modrinth.index.json"))
    # every file the pack lists, downloaded once and checked against its sha1
    for entry in index["files"]:
        cached = cache / entry["hashes"]["sha1"]
        if not cached.exists():
            request = urllib.request.Request(entry["downloads"][0], headers={"User-Agent": "raihanadf/blossom"})
            data = urllib.request.urlopen(request).read()
            if hashlib.sha1(data).hexdigest() != entry["hashes"]["sha1"]:
                raise SystemExit(f"sha1 mismatch for {entry['path']}")
            cached.write_bytes(data)
        target = stage / "minecraft" / entry["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(cached.read_bytes())
    for name in pack.namelist():
        if name.startswith("overrides/") and not name.endswith("/"):
            target = stage / "minecraft" / name.removeprefix("overrides/")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(pack.read(name))
    icon = zipfile.ZipFile(pack.open(f"overrides/mods/blossom-{props['version']}.jar")).read("assets/blossom/icon.png")

(stage.parent / "blossom.png").write_bytes(icon)
(stage / "instance.cfg").write_text(
    "[General]\nConfigVersion=1.3\nInstanceType=OneSix\nname=Blossom\niconKey=blossom\n"
    "AutomaticJava=true\nOverrideMemory=true\nMaxMemAlloc=4096\nMinMemAlloc=1024\n")
(stage / "mmc-pack.json").write_text(json.dumps({
    "components": [
        {"uid": "net.minecraft", "version": index["dependencies"]["minecraft"], "important": True},
        {"uid": "net.fabricmc.fabric-loader", "version": index["dependencies"]["fabric-loader"]},
    ],
    "formatVersion": 1,
}, indent=4))

# checksums so unchanged jars are left alone (windows refuses to replace jars of a running game)
rsync = ["rsync", "-rtc", "--no-perms"]
for prism in PRISMS:
    instance = f"{prism}/instances/Blossom"
    host, _, path = instance.rpartition(":")
    mkdir = ["mkdir", "-p", f"{path}/minecraft/mods"]
    subprocess.run(["ssh", host, " ".join(f"'{part}'" for part in mkdir)] if host else mkdir, check=True)
    # mods and resource packs: exactly what the pack lists
    subprocess.run(rsync + ["--delete", f"{mods}/", f"{instance}/minecraft/mods/"], check=True)
    # configs and other defaults only fill gaps, so settings changed in game survive updates
    subprocess.run(rsync + ["--ignore-existing", "--exclude", "mods/", f"{stage}/minecraft/", f"{instance}/minecraft/"], check=True)
    # instance.cfg keeps prism's play time and per instance settings after the first install
    subprocess.run(rsync + ["--ignore-existing", f"{stage}/instance.cfg", f"{instance}/"], check=True)
    subprocess.run(rsync + [f"{stage}/mmc-pack.json", f"{instance}/"], check=True)
    subprocess.run(rsync + [f"{stage.parent}/blossom.png", f"{prism}/icons/"], check=True)
    print(f"installed {len(list(mods.iterdir()))} mods into {instance}")
