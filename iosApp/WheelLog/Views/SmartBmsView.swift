import SwiftUI
import WheelLogCore

/// Convenience accessor for KMP BmsSnapshot cells (KotlinArray<KotlinDouble> → [Double]).
extension BmsSnapshot {
    var cellValues: [Double] {
        (0..<Int(cellNum)).map { (cells.get(index: Int32($0)) as! NSNumber).doubleValue }
    }
}

struct SmartBmsView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("BMS 1")
                    .font(.headline)
                if let bms = wheelManager.wheelState.bms1 {
                    BmsBlock(bms: bms)
                } else {
                    Text("No BMS 1 data")
                        .foregroundColor(.secondary)
                }

                Divider()

                Text("BMS 2")
                    .font(.headline)
                if let bms = wheelManager.wheelState.bms2 {
                    BmsBlock(bms: bms)
                } else {
                    Text("No BMS 2 data")
                        .foregroundColor(.secondary)
                }
            }
            .padding()
        }
        .navigationTitle("Battery")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct BmsBlock: View {
    let bms: BmsSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !bms.serialNumber.isEmpty {
                Text("Serial: \(bms.serialNumber)")
            }
            Text("Voltage: \(DisplayUtils.shared.formatBmsVoltage(voltage: bms.voltage))")
            Text("Current: \(DisplayUtils.shared.formatBmsCurrent(current: bms.current))")
            if bms.remCap > 0 {
                Text("Remaining: \(bms.remCap) / \(bms.factoryCap) mAh (\(bms.remPerc)%)")
            }
            Text("Temp 1: \(DisplayUtils.shared.formatBmsTemperature(celsius: bms.temp1))")
            Text("Temp 2: \(DisplayUtils.shared.formatBmsTemperature(celsius: bms.temp2))")
            if bms.health > 0 {
                Text("Health: \(bms.health)%")
            }
            Text("Max Cell: \(DisplayUtils.shared.formatBmsCellLabeled(voltage: bms.maxCell, cellNum: bms.maxCellNum))")
            Text("Min Cell: \(DisplayUtils.shared.formatBmsCellLabeled(voltage: bms.minCell, cellNum: bms.minCellNum))")
            Text("Cell Diff: \(DisplayUtils.shared.formatBmsCell(voltage: bms.cellDiff))")
            Text("Avg Cell: \(DisplayUtils.shared.formatBmsCell(voltage: bms.avgCell))")

            if bms.cellNum > 0 {
                Divider()
                Text("Cells (\(bms.cellNum)):")
                    .font(.subheadline)
                    .fontWeight(.medium)
                ForEach(0..<Int(bms.cellNum), id: \.self) { i in
                    Text("  \(DisplayUtils.shared.formatBmsCellIndexed(index: Int32(i + 1), voltage: bms.cellValues[i]))")
                        .font(.system(.body, design: .monospaced))
                }
            }
        }
    }
}
