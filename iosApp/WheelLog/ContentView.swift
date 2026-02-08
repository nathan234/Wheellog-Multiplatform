import SwiftUI

struct ContentView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        NavigationStack {
            ZStack {
                if wheelManager.connectionState.isConnected {
                    DashboardView()
                } else {
                    ScanView()
                }

                VStack {
                    ConnectionBanner()
                    Spacer()
                }
            }
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(WheelManager())
}
