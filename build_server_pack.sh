#!/bin/bash
set -e

echo "============================================="
echo " Thaumic Wards - Server Pack Builder"
echo "============================================="
echo ""

# Check for modpack zip argument
if [ -z "$1" ]; then
    echo "Usage: ./build_server_pack.sh /path/to/modpack.zip"
    echo ""
    echo "This script builds a server-ready pack from a CurseForge modpack:"
    echo "  - Extracts the modpack"
    echo "  - Removes conflicting mods (FTB Chunks, FTB Teams, Chunk Loaders, Chicken Chunks)"
    echo "  - Adds the Thaumic Wards mod"
    echo "  - Generates optimized start scripts"
    exit 1
fi

MODPACK_ZIP="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/MagicSMP-ServerPack"
MOD_JAR="${SCRIPT_DIR}/build/libs/thaumic_wards-1.0.0.jar"

# Check modpack zip exists
if [ ! -f "$MODPACK_ZIP" ]; then
    echo "ERROR: Modpack zip not found: $MODPACK_ZIP"
    exit 1
fi

# Check mod jar exists
if [ ! -f "$MOD_JAR" ]; then
    echo "WARNING: Thaumic Wards jar not found. Building..."
    cd "$SCRIPT_DIR"
    ./gradlew build
fi

# Clean and create output directory
if [ -d "$OUTPUT_DIR" ]; then
    echo "Cleaning existing output directory..."
    rm -rf "$OUTPUT_DIR"
fi
mkdir -p "$OUTPUT_DIR"

echo "Extracting modpack..."
unzip -q "$MODPACK_ZIP" -d "$OUTPUT_DIR/temp"

# Move overrides to root
if [ -d "$OUTPUT_DIR/temp/overrides" ]; then
    cp -r "$OUTPUT_DIR/temp/overrides/"* "$OUTPUT_DIR/"
fi

# Copy manifest for reference
if [ -f "$OUTPUT_DIR/temp/manifest.json" ]; then
    cp "$OUTPUT_DIR/temp/manifest.json" "$OUTPUT_DIR/"
fi

# Clean temp
rm -rf "$OUTPUT_DIR/temp"

# Create mods directory if not exists
mkdir -p "$OUTPUT_DIR/mods"

echo ""
echo "Removing conflicting mods..."
REMOVED=0

for pattern in "ftbchunks-*.jar" "ftbteams-*.jar" "chunkloaders-*.jar" "chickenchunks-*.jar"; do
    for f in "$OUTPUT_DIR/mods/"$pattern; do
        if [ -f "$f" ]; then
            echo "  Removing: $(basename "$f")"
            rm "$f"
            REMOVED=$((REMOVED + 1))
        fi
    done
done
echo "  Removed $REMOVED conflicting mod(s)."

echo ""
echo "Adding Thaumic Wards..."
cp "$MOD_JAR" "$OUTPUT_DIR/mods/"
echo "  Added thaumic_wards-1.0.0.jar"

# Generate start.sh
echo ""
echo "Generating start scripts..."
cat > "$OUTPUT_DIR/start.sh" << 'STARTEOF'
#!/bin/bash
echo "Starting Thaumic Wards SMP Server..."
java -Xmx8G -Xms4G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=150 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:+UseNUMA \
  -XX:ParallelGCThreads=20 \
  -XX:ConcGCThreads=5 \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -jar forge-1.16.5-36.2.34.jar nogui
STARTEOF
chmod +x "$OUTPUT_DIR/start.sh"

# Generate start.bat for Windows users
cat > "$OUTPUT_DIR/start.bat" << 'BATEOF'
@echo off
echo Starting Thaumic Wards SMP Server...
echo.
java -Xmx8G -Xms4G ^
  -XX:+UseG1GC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:MaxGCPauseMillis=150 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+DisableExplicitGC ^
  -XX:+AlwaysPreTouch ^
  -XX:+UseNUMA ^
  -XX:ParallelGCThreads=20 ^
  -XX:ConcGCThreads=5 ^
  -XX:G1NewSizePercent=30 ^
  -XX:G1MaxNewSizePercent=40 ^
  -XX:G1HeapRegionSize=8M ^
  -XX:G1ReservePercent=20 ^
  -XX:G1HeapWastePercent=5 ^
  -XX:G1MixedGCCountTarget=4 ^
  -XX:InitiatingHeapOccupancyPercent=45 ^
  -XX:G1MixedGCLiveThresholdPercent=90 ^
  -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:SurvivorRatio=32 ^
  -XX:+PerfDisableSharedMem ^
  -XX:MaxTenuringThreshold=1 ^
  -jar forge-1.16.5-36.2.34.jar nogui
pause
BATEOF

# Generate eula.txt
echo "eula=true" > "$OUTPUT_DIR/eula.txt"

echo ""
echo "============================================="
echo " Server pack built successfully!"
echo " Output: $OUTPUT_DIR"
echo "============================================="
echo ""
echo "Next steps:"
echo "  1. Install Forge 1.16.5-36.2.34 server in the output directory"
echo "  2. Download mods from CurseForge using the manifest.json"
echo "  3. Run ./start.sh to launch the server"
