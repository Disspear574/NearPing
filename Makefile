.PHONY: core project build run test dmg clean

test:
	./gradlew :core:jvmTest

core:
	./gradlew :core:installXCFramework

project: core
	cd macos && xcodegen generate

build: project
	cd macos && xcodebuild -project NearPing.xcodeproj -scheme NearPing \
		-configuration Debug -derivedDataPath .build -allowProvisioningUpdates build

run: build
	open macos/.build/Build/Products/Debug/NearPing.app

# Ad-hoc signed release image: Gatekeeper on another Mac asks for explicit approval.
dmg: project
	cd macos && xcodebuild -project NearPing.xcodeproj -scheme NearPing \
		-configuration Release -derivedDataPath .build -allowProvisioningUpdates build
	rm -rf macos/.dmg && mkdir -p macos/.dmg
	cp -R macos/.build/Build/Products/Release/NearPing.app macos/.dmg/
	ln -s /Applications macos/.dmg/Applications
	rm -f NearPing.dmg
	hdiutil create -volname NearPing -srcfolder macos/.dmg -ov -format UDZO NearPing.dmg
	rm -rf macos/.dmg
	@echo "done: NearPing.dmg"

clean:
	./gradlew clean
	rm -rf macos/.build macos/.dmg macos/NearPing.xcodeproj macos/Frameworks NearPing.dmg
