#!/bin/zsh
# Builds build/NearPing.dmg: ad-hoc signed Release app, background with logo, app -> Applications, volume and file icon.
set -euo pipefail
cd "$(dirname "$0")/.."

make project >/dev/null
# Ad hoc on purpose: a published image never carries a signing team.
( cd apps/appleApp && xcodebuild -project NearPing.xcodeproj -scheme NearPing -configuration Release \
    -derivedDataPath .build CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM= build -quiet )
APP=apps/appleApp/.build/Build/Products/Release/NearPing.app
ICON=apps/appleApp/App/Assets.xcassets/AppIcon.appiconset/icon-256@1x.png
VOL="NearPing"
TMP=build/dmg
OUT=build/NearPing.dmg
mkdir -p $TMP
# Outside the home folder: Finder stores the image path in the volume's .DS_Store.
WORK=$(mktemp -d /tmp/nearping-dmg.XXXXXX)
STAGE=$WORK/stage
RW=$WORK/rw.dmg

swiftc -O scripts/make-dmg-background.swift -o $TMP/make-dmg-background
$TMP/make-dmg-background $TMP/bg.png 1 $ICON
$TMP/make-dmg-background $TMP/bg@2x.png 2 $ICON
tiffutil -cathidpicheck $TMP/bg.png $TMP/bg@2x.png -out $TMP/background.tiff >/dev/null

mkdir -p $STAGE/.background
cp -R $APP $STAGE/
cp $TMP/background.tiff $STAGE/.background/background.tiff
ln -s /Applications $STAGE/Applications

if [[ -d "/Volumes/$VOL" ]]; then hdiutil detach "/Volumes/$VOL" -quiet || true; fi
hdiutil create -srcfolder $STAGE -volname "$VOL" -fs HFS+ -format UDRW -size 80m $RW -quiet
MOUNT=$(hdiutil attach -readwrite -noverify -noautoopen $RW | awk -F'\t' '/\/Volumes\//{print $NF}')

osascript <<OSA
tell application "Finder"
  tell disk "$VOL"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {200, 120, 840, 568}
    set opts to the icon view options of container window
    set arrangement of opts to not arranged
    set icon size of opts to 96
    set text size of opts to 13
    set background picture of opts to file ".background:background.tiff"
    set position of item "NearPing.app" of container window to {170, 255}
    set position of item "Applications" of container window to {470, 255}
    update without registering applications
    delay 1
    close
  end tell
end tell
OSA

# After Finder: opening the window deletes .VolumeIcon.icns and clears the custom icon flag.
cp $APP/Contents/Resources/AppIcon.icns "$MOUNT/.VolumeIcon.icns"
SetFile -a V "$MOUNT/.VolumeIcon.icns"
SetFile -a C "$MOUNT"
rm -r "$MOUNT/.fseventsd" 2>/dev/null || true
sync
hdiutil detach "$MOUNT" -quiet
rm -f "$OUT"
hdiutil convert $RW -format UDZO -imagekey zlib-level=9 -o "$OUT" -quiet
rm -r "$WORK"

# Icon on the .dmg file itself (kept by AirDrop and zip; messengers may drop it).
swift -e 'import AppKit; NSWorkspace.shared.setIcon(NSImage(contentsOfFile: CommandLine.arguments[1]), forFile: CommandLine.arguments[2], options: [])' $APP/Contents/Resources/AppIcon.icns "$OUT" 2>/dev/null || true
echo "$OUT"
