.PHONY: core project build run test clean

test:
	./gradlew :core:jvmTest

core:
	./gradlew :core:installXCFramework

project: core
	cd macos && xcodegen generate

build: project
	cd macos && xcodebuild -project Earshot.xcodeproj -scheme Earshot \
		-configuration Debug -derivedDataPath .build build

run: build
	open macos/.build/Build/Products/Debug/Earshot.app

clean:
	./gradlew clean
	rm -rf macos/.build macos/Earshot.xcodeproj macos/Frameworks
