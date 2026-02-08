import SwiftUI

@main
struct WheelLogApp: App {
    @StateObject private var wheelManager = WheelManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(wheelManager)
        }
    }
}
