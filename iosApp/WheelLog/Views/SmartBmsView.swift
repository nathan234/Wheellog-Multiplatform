import SwiftUI

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
    let bms: BmsSnapshotWrapper

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !bms.serialNumber.isEmpty {
                Text("Serial: \(bms.serialNumber)")
            }
            Text(String(format: "Voltage: %.2f V", bms.voltage))
            Text(String(format: "Current: %.2f A", bms.current))
            if bms.remCap > 0 {
                Text("Remaining: \(bms.remCap) / \(bms.factoryCap) mAh (\(bms.remPerc)%)")
            }
            Text(String(format: "Temp 1: %.1f\u{00B0}C", bms.temp1))
            Text(String(format: "Temp 2: %.1f\u{00B0}C", bms.temp2))
            if bms.health > 0 {
                Text("Health: \(bms.health)%")
            }
            Text(String(format: "Max Cell: %.3f V [%d]", bms.maxCell, bms.maxCellNum))
            Text(String(format: "Min Cell: %.3f V [%d]", bms.minCell, bms.minCellNum))
            Text(String(format: "Cell Diff: %.3f V", bms.cellDiff))
            Text(String(format: "Avg Cell: %.3f V", bms.avgCell))

            if bms.cellNum > 0 {
                Divider()
                Text("Cells (\(bms.cellNum)):")
                    .font(.subheadline)
                    .fontWeight(.medium)
                ForEach(0..<bms.cellNum, id: \.self) { i in
                    Text(String(format: "  #%d: %.3f V", i + 1, bms.cells[i]))
                        .font(.system(.body, design: .monospaced))
                }
            }
        }
    }
}
