import SwiftUI

@main
struct WheelLogApp: App {
    @StateObject private var wheelManager = WheelManager()
    @Environment(\.scenePhase) var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(wheelManager)
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background:
                wheelManager.onEnterBackground()
            case .active:
                wheelManager.onEnterForeground()
            default:
                break
            }
        }
    }
}
