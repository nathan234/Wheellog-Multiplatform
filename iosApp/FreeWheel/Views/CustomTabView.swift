import SwiftUI
import FreeWheelCore

/// Renders a custom tab using DashboardContentView with controls hidden.
/// Shows placeholder when disconnected.
struct CustomTabView: View {
    @EnvironmentObject var wheelManager: WheelManager
    let tabId: String

    @State private var selectedMetric: String?
    @State private var showEditLayout = false

    private var layout: DashboardLayout {
        wheelManager.customTabLayouts[tabId] ?? DashboardLayout.companion.default()
    }

    private var tabLabel: String {
        for tab in Array(wheelManager.navigationConfig.tabs) {
            if let custom = tab as? NavigationTab.Custom, custom.id == tabId {
                return custom.label
            }
        }
        return "Custom"
    }

    private var showMetricBinding: Binding<Bool> {
        Binding(
            get: { selectedMetric != nil },
            set: { if !$0 { selectedMetric = nil } }
        )
    }

    var body: some View {
        Group {
            if !wheelManager.connectionState.isConnected {
                VStack {
                    Spacer()
                    Text("Connect to a wheel to see data")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else {
                DashboardContentView(
                    layout: layout,
                    showControls: false,
                    selectedMetric: $selectedMetric,
                    showChart: .constant(false),
                    showBms: .constant(false),
                    showEditDashboard: .constant(false)
                )
            }
        }
        .navigationTitle(tabLabel)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showEditLayout = true }) {
                    Image(systemName: "square.and.pencil")
                }
            }
        }
        .navigationDestination(isPresented: showMetricBinding) {
            MetricDetailView(metricId: selectedMetric ?? "speed")
        }
        .sheet(isPresented: $showEditLayout) {
            NavigationStack {
                CustomTabEditView(tabId: tabId)
            }
        }
    }
}
