import Foundation
@preconcurrency import core

extension Data {
    var asKotlinByteArray: KotlinByteArray {
        let out = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            out.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return out
    }
}

extension KotlinByteArray {
    var asData: Data {
        var bytes = [UInt8]()
        bytes.reserveCapacity(Int(size))
        for index in 0..<size {
            bytes.append(UInt8(bitPattern: get(index: index)))
        }
        return Data(bytes)
    }
}
