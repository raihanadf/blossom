#!/usr/bin/env python3
# builds build/blossom-<version>.mrpack: fabulously optimized as the base, our jar on top.
# run ./gradlew build first. fo already ships fabric api and e4mc.
import io
import json
import urllib.request
import zipfile
from pathlib import Path

FO_VERSION = "15.0.0-beta.2"

root = Path(__file__).resolve().parent.parent
props = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
version = props["version"]
jar = root / "build" / "libs" / f"blossom-{version}.jar"
if not jar.exists():
    raise SystemExit(f"{jar} not found, run ./gradlew build first")

api = f"https://api.modrinth.com/v2/project/fabulously-optimized/version/{FO_VERSION}"
request = urllib.request.Request(api, headers={"User-Agent": "raihanadf/blossom"})
meta = json.load(urllib.request.urlopen(request))
fo_url = next(f["url"] for f in meta["files"] if f["primary"])
fo_bytes = urllib.request.urlopen(urllib.request.Request(fo_url, headers={"User-Agent": "raihanadf/blossom"})).read()

out = root / "build" / f"blossom-{version}.mrpack"
with zipfile.ZipFile(io.BytesIO(fo_bytes)) as base, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as pack:
    for entry in base.infolist():
        data = base.read(entry)
        if entry.filename == "modrinth.index.json":
            index = json.loads(data)
            if index["dependencies"]["minecraft"] != props["minecraft_version"]:
                raise SystemExit(f"fo {FO_VERSION} targets {index['dependencies']['minecraft']}, we target {props['minecraft_version']}")
            index["name"] = "Blossom"
            index["versionId"] = version
            index["summary"] = f"blossom {version} on fabulously optimized {FO_VERSION}"
            data = json.dumps(index, indent=1).encode()
        pack.writestr(entry.filename, data)
    pack.write(jar, f"overrides/mods/{jar.name}")

print(out)
