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

# Ad-hoc signed image with a background, for releases.
dmg:
	./scripts/make-dmg.sh

clean:
	./gradlew clean
	rm -rf macos/.build macos/NearPing.xcodeproj macos/Frameworks build/NearPing.dmg build/dmg
