import SwiftUI
import FreeWheelCore

struct ContentView: View {
    @EnvironmentObject var wheelManager: WheelManager

    /// Devices-tab routing predicate. `Connected` shows the live dashboard;
    /// `wheelTypeRequired` keeps the dashboard mounted so the
    /// `WheelTypePickerSheet` can present against the still-live peripheral.
    private var hostsDashboard: Bool {
        if wheelManager.connectionState.isConnected { return true }
        if case .wheelTypeRequired = wheelManager.connectionState { return true }
        return false
    }

    var body: some View {
        TabView {
            ForEach(Array(wheelManager.navigationConfig.tabs), id: \.id) { tab in
                NavigationStack {
                    tabContent(for: tab)
                }
                .tabItem {
                    Label(tab.label, systemImage: sfSymbol(for: tab.iconName))
                }
            }
        }
    }

    @ViewBuilder
    private func tabContent(for tab: NavigationTab) -> some View {
        if tab is NavigationTab.Devices {
            VStack(spacing: 0) {
                ConnectionBanner()
                if hostsDashboard {
                    // Pass 4: WheelTypeRequired hosts the picker .sheet from
                    // DashboardView. Routing to ScanView there would unmount
                    // the sheet before it presents — leaving the user stuck
                    // on the scan list with the row in a pseudo-connecting
                    // state and no way to confirm a wheel type.
                    DashboardView()
                } else {
                    ScanView()
                }
            }
        } else if tab is NavigationTab.Chart {
            TelemetryChartView()
        } else if tab is NavigationTab.Bms {
            SmartBmsView()
        } else if tab is NavigationTab.Rides {
            RidesView()
        } else if tab is NavigationTab.Map {
            LiveRideMapScreen()
        } else if tab is NavigationTab.WheelSettings {
            WheelSettingsView()
        } else if tab is NavigationTab.Charger {
            ChargerView()
        } else if tab is NavigationTab.Settings {
            SettingsView()
        } else if let custom = tab as? NavigationTab.Custom {
            CustomTabView(tabId: custom.id)
        } else {
            Text("Unknown tab")
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(WheelManager())
}
