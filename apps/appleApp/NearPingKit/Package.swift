// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "NearPingKit",
    platforms: [.macOS(.v26)],
    products: [
        .library(name: "NearPingPlatform", targets: ["NearPingPlatform"]),
        .library(name: "NearPingUI", targets: ["NearPingUI"]),
    ],
    targets: [
        .binaryTarget(name: "core", path: "Frameworks/core.xcframework"),
        .target(name: "NearPingPlatform", dependencies: ["core"]),
        .target(name: "NearPingUI", dependencies: ["NearPingPlatform", "core"]),
    ],
    swiftLanguageModes: [.v6]
)
