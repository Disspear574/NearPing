.PHONY: core project build run test dmg clean

test:
	./gradlew :core:jvmTest

core:
	./gradlew :core:installXCFramework

project: core
	cd apps/appleApp && xcodegen generate

build: project
	cd apps/appleApp && xcodebuild -project NearPing.xcodeproj -scheme NearPing \
		-configuration Debug -derivedDataPath .build -allowProvisioningUpdates build

run: build
	open apps/appleApp/.build/Build/Products/Debug/NearPing.app

# Ad-hoc signed image with a background, for releases.
dmg:
	./scripts/make-dmg.sh

clean:
	./gradlew clean
	rm -rf apps/appleApp/.build apps/appleApp/NearPing.xcodeproj apps/appleApp/NearPingKit/Frameworks build/NearPing.dmg build/dmg
