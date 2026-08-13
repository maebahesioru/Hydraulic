#!/usr/bin/env python3
"""
Inject the Geyser 2.4.4 backport patches into the official Geyser-NeoForge jar.

Usage:
    python inject-geyser-patch.py <official-geyser-neoforge.jar> <geyser-build-classes-dir> <output.jar>

The patched classes are compiled from the Geyser 2.4.4 source (commit d61ad7b) with
patches/geyser-2.4.4.patch applied, then copied over the official jar's classes.
This avoids rebuilding the whole jar (jarjar/languages issues) while keeping the
official packaging intact.

Patched classes:
    org/geysermc/geyser/registry/populator/BlockRegistryPopulator.class
        - registerWithAnyIndex for BLOCK_STATES so Hydraulic's non-vanilla blocks
          (runtime IDs beyond the vanilla range) don't crash with
          IndexOutOfBoundsException.
    org/geysermc/geyser/registry/loader/CollisionRegistryLoader.class
        - Skip collision generation for non-vanilla (custom) blocks that have no
          entry in the vanilla indices array.

Setup before running:
    # 1. clone Geyser at commit d61ad7b
    git clone https://github.com/GeyserMC/Geyser.git
    cd Geyser && git checkout d61ad7b
    # 2. apply the patch (this file lives in the Hydraulic repo)
    git apply <hydraulic>/patches/geyser-2.4.4.patch
    # 3. build only the core classes (JDK 21)
    ./gradlew :core:compileJava
"""
import sys
import zipfile

def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    official_jar, classes_dir, output = sys.argv[1], sys.argv[2], sys.argv[3]
    patches = {
        "org/geysermc/geyser/registry/populator/BlockRegistryPopulator.class": None,
        "org/geysermc/geyser/registry/loader/CollisionRegistryLoader.class": None,
    }
    import os
    for rel in patches:
        path = os.path.join(classes_dir, rel.replace("/", os.sep))
        if not os.path.exists(path):
            print("Missing compiled class:", path)
            sys.exit(1)
        with open(path, "rb") as f:
            patches[rel] = f.read()

    with zipfile.ZipFile(official_jar) as zin:
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename in patches:
                    zout.writestr(item.filename, patches[item.filename])
                    print("patched:", item.filename)
                else:
                    zout.writestr(item, zin.read(item.filename))
    print("wrote:", output)

if __name__ == "__main__":
    main()
