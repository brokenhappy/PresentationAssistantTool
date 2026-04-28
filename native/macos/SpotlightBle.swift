import CoreBluetooth
import Foundation

private let LOGI_SERVICE = CBUUID(string: "00010000-0000-1000-8000-011F2000046D")
private let LOGI_CHAR    = CBUUID(string: "00010001-0000-1000-8000-011F2000046D")
private let DEVICE_INFO  = CBUUID(string: "180A")

private var instance: SpotlightBle?

final class SpotlightBle: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private let queue = DispatchQueue(label: "spotlight-ble")
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var characteristic: CBCharacteristic?
    private var presenterCtrlIdx: UInt8 = 0
    private var awaitingFeatureIndex = false

    private var _connected = false
    private var _ready = false

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: queue)
    }

    func connect() {
        queue.async { self.findAndConnect() }
    }

    private func findAndConnect() {
        guard central.state == .poweredOn else { return }
        for p in central.retrieveConnectedPeripherals(withServices: [DEVICE_INFO]) {
            if p.name?.uppercased().contains("SPOTLIGHT") == true {
                peripheral = p
                p.delegate = self
                central.connect(p)
                return
            }
        }
    }

    func disconnect() {
        queue.async {
            if let p = self.peripheral {
                self.central.cancelPeripheralConnection(p)
            }
            self.cleanup()
        }
    }

    private func cleanup() {
        peripheral = nil
        characteristic = nil
        presenterCtrlIdx = 0
        awaitingFeatureIndex = false
        _connected = false
        _ready = false
    }

    func isConnected() -> Bool { return _connected }
    func isReady() -> Bool { return _ready }

    func vibrate(duration100ms: UInt8) -> Bool {
        guard _ready else { return false }
        let idx = presenterCtrlIdx
        queue.async {
            guard let c = self.characteristic, let p = self.peripheral else { return }
            let funcSwId: UInt8 = (1 << 4) | 0x07
            let msg: [UInt8] = [idx, funcSwId, duration100ms, 0xE8, 0x80, 0x00, 0x00, 0x00]
            p.writeValue(Data(msg), for: c, type: .withResponse)
        }
        return true
    }

    // MARK: - CBCentralManagerDelegate

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            findAndConnect()
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        _connected = true
        peripheral.discoverServices([LOGI_SERVICE])
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        cleanup()
    }

    // MARK: - CBPeripheralDelegate

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let service = peripheral.services?.first(where: { $0.uuid == LOGI_SERVICE }) else { return }
        peripheral.discoverCharacteristics([LOGI_CHAR], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let c = service.characteristics?.first(where: { $0.uuid == LOGI_CHAR }) else { return }
        characteristic = c
        peripheral.setNotifyValue(true, for: c)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil, let c = self.characteristic, let p = self.peripheral else { return }
        awaitingFeatureIndex = true
        let msg: [UInt8] = [0x00, 0x07, 0x1A, 0x00, 0x00, 0x00, 0x00, 0x00]
        p.writeValue(Data(msg), for: c, type: .withResponse)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {}

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        let bytes = [UInt8](data)

        if awaitingFeatureIndex && bytes.count >= 3 && bytes[0] == 0x00 {
            awaitingFeatureIndex = false
            let idx = bytes[2]
            if idx != 0 {
                presenterCtrlIdx = idx
                _ready = true
            }
        }
    }
}

// MARK: - C API

@_cdecl("spotlight_ble_init")
public func spotlightBleInit() {
    if instance == nil {
        instance = SpotlightBle()
    }
    instance?.connect()
}

@_cdecl("spotlight_ble_is_connected")
public func spotlightBleIsConnected() -> Bool {
    return instance?.isConnected() ?? false
}

@_cdecl("spotlight_ble_is_ready")
public func spotlightBleIsReady() -> Bool {
    return instance?.isReady() ?? false
}

@_cdecl("spotlight_ble_vibrate")
public func spotlightBleVibrate(_ duration100ms: UInt8) -> Bool {
    return instance?.vibrate(duration100ms: duration100ms) ?? false
}

@_cdecl("spotlight_ble_cleanup")
public func spotlightBleCleanup() {
    instance?.disconnect()
    instance = nil
}
