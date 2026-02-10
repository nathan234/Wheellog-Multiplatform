import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        Form {
            Section("Units") {
                Toggle("Use Miles per Hour", isOn: $wheelManager.useMph)
                Toggle("Use Fahrenheit", isOn: $wheelManager.useFahrenheit)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        SettingsView()
            .environmentObject(WheelManager())
    }
}
