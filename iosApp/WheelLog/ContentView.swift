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
        // Reset NavigationStack (pop any pushed views like Settings) when
        // transitioning between connected and disconnected states.
        .id(wheelManager.connectionState.isConnected)
    }
}

#Preview {
    ContentView()
        .environmentObject(WheelManager())
}
