import SwiftUI

struct ContentView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        TabView {
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
            .id(wheelManager.connectionState.isConnected)
            .tabItem {
                Label("Devices", systemImage: "antenna.radiowaves.left.and.right")
            }

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Label("Settings", systemImage: "gearshape")
            }
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(WheelManager())
}
