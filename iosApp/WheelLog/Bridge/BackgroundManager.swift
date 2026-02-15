import Foundation
import UserNotifications
import UIKit

@MainActor
class BackgroundManager: ObservableObject {

    @Published var isInBackground: Bool = false

    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid

    nonisolated init() {}

    // MARK: - Notification Permission

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("Notification permission error: \(error)")
            }
        }
    }

    // MARK: - Alarm Notification

    func postAlarmNotification(type: AlarmDisplayType, value: String) {
        let content = UNMutableNotificationContent()
        content.title = "WheelLog Alarm"
        content.body = value
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "alarm_\(type.rawValue)_\(Date().timeIntervalSince1970)",
            content: content,
            trigger: nil  // Deliver immediately
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Notification error: \(error)")
            }
        }
    }

    // MARK: - Background Task

    func beginBackgroundTask() {
        guard backgroundTaskID == .invalid else { return }
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "WheelLogBLE") { [weak self] in
            self?.endBackgroundTask()
        }
        isInBackground = true
    }

    func endBackgroundTask() {
        isInBackground = false
        guard backgroundTaskID != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskID)
        backgroundTaskID = .invalid
    }
}
