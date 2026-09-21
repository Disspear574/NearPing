.PHONY: core project build run test dmg clean

test:
	./gradlew :core:jvmTest

core:
	./gradlew :core:installXCFramework

project: core
	cd macos && xcodegen generate

build: project
	cd macos && xcodebuild -project Earshot.xcodeproj -scheme Earshot \
		-configuration Debug -derivedDataPath .build -allowProvisioningUpdates build

run: build
	open macos/.build/Build/Products/Debug/Earshot.app

# Release-сборка и образ для переноса на другую машину.
# Подпись локальная (ad-hoc): Gatekeeper на чужом маке потребует явного разрешения.
dmg: project
	cd macos && xcodebuild -project Earshot.xcodeproj -scheme Earshot \
		-configuration Release -derivedDataPath .build -allowProvisioningUpdates build
	rm -rf macos/.dmg && mkdir -p macos/.dmg
	cp -R macos/.build/Build/Products/Release/Earshot.app macos/.dmg/
	ln -s /Applications macos/.dmg/Applications
	rm -f Earshot.dmg
	hdiutil create -volname Earshot -srcfolder macos/.dmg -ov -format UDZO Earshot.dmg
	rm -rf macos/.dmg
	@echo "готово: Earshot.dmg"

clean:
	./gradlew clean
	rm -rf macos/.build macos/.dmg macos/Earshot.xcodeproj macos/Frameworks Earshot.dmg
