#!/bin/bash
#====================================================================
# Rebuilds the Overseer application and plasmoid and packages both
# into a single Debian package at deploy/overseer_<version>_all.deb
#
# Copyright Jody Sankey 2026
#
# This software may be modified and distributed under the terms of
# the MIT license. See the LICENSE.md file for details.
#====================================================================
set -euo pipefail
umask 022

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLASMOID_ID=com.jsankey.overseer.monitor

# The application's version string is the single source of truth.
VERSION=$(sed -n 's/.*VERSION_STRING = "\([^"]*\)".*/\1/p' \
    "$PROJECT_DIR/src/main/java/com/jsankey/overseer/Configuration.java")
if [ -z "$VERSION" ] ; then
    echo "Could not determine version from Configuration.java" >&2
    exit 1
fi

PACKAGE=overseer_${VERSION}_all
STAGE="$PROJECT_DIR/build/deb/$PACKAGE"
OUTPUT="$PROJECT_DIR/deploy/$PACKAGE.deb"

echo "=== Building Overseer $VERSION"
# No daemon: on NFS a daemon holding test output open blocks the next clean.
(cd "$PROJECT_DIR" && ./gradlew --no-daemon clean test shadowJar)

echo "=== Checking plasmoid"
python3 -m json.tool "$PROJECT_DIR/plasmoid/metadata.json" > /dev/null
test -f "$PROJECT_DIR/plasmoid/contents/ui/main.qml"

echo "=== Staging package in $STAGE"
rm -rf "$STAGE"

# Application
install -D -m 644 "$PROJECT_DIR/build/libs/OverseerBundle.jar" \
    "$STAGE/usr/share/overseer/OverseerBundle.jar"
install -D -m 644 "$PROJECT_DIR/overseer_check.html" \
    "$STAGE/usr/share/overseer/overseer_check.html"
install -D -m 755 "$PROJECT_DIR/overseer_check.py" "$STAGE/usr/bin/overseer-check"
cat > "$STAGE/usr/bin/overseer" <<'EOF'
#!/bin/sh
exec java -jar /usr/share/overseer/OverseerBundle.jar "$@"
EOF
chmod 755 "$STAGE/usr/bin/overseer"

# Plasmoid
PLASMOID_DIR="$STAGE/usr/share/plasma/plasmoids/$PLASMOID_ID"
mkdir -p "$PLASMOID_DIR"
cp -r "$PROJECT_DIR/plasmoid/metadata.json" "$PROJECT_DIR/plasmoid/contents" "$PLASMOID_DIR"
find "$PLASMOID_DIR" -type f -exec chmod 644 {} +

# Documentation
install -D -m 644 "$PROJECT_DIR/LICENCE.md" "$STAGE/usr/share/doc/overseer/copyright"
install -D -m 644 "$PROJECT_DIR/deploy/example.overseer.service" \
    "$STAGE/usr/share/doc/overseer/examples/overseer.service"

find "$STAGE" -type d -exec chmod 755 {} +

# Package metadata
mkdir -p "$STAGE/DEBIAN"
cat > "$STAGE/DEBIAN/control" <<EOF
Package: overseer
Version: $VERSION
Architecture: all
Maintainer: Jody Sankey <software@jsankey.com>
Installed-Size: $(du -sk --exclude=DEBIAN "$STAGE" | cut -f1)
Depends: java17-runtime-headless, network-manager
Recommends: qml6-module-qtwebsockets, plasma-workspace (>= 4:6.0), python3
Section: utils
Priority: optional
Description: Periodic and gated task execution daemon
 Overseer runs a set of commands periodically while certain conditions are
 met and reports the outcome of those commands over a WebSocket interface.
 .
 This package also contains a KDE Plasma 6 widget that displays the
 execution status. An example systemd user unit is provided in
 /usr/share/doc/overseer/examples.
EOF

echo "=== Building $OUTPUT"
dpkg-deb --build --root-owner-group "$STAGE" "$OUTPUT"
dpkg-deb --info "$OUTPUT"
dpkg-deb --contents "$OUTPUT"
echo "Created package at $OUTPUT"
