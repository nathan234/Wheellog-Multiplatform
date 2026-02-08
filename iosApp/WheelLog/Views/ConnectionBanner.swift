import SwiftUI

struct ConnectionBanner: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        if shouldShowBanner {
            HStack(spacing: 8) {
                statusIcon
                Text(wheelManager.connectionState.statusText)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Spacer()
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(bannerColor)
            .foregroundColor(.white)
            .transition(.move(edge: .top).combined(with: .opacity))
            .animation(.spring(response: 0.3), value: shouldShowBanner)
        }
    }

    private var shouldShowBanner: Bool {
        switch wheelManager.connectionState {
        case .disconnected:
            return false
        case .scanning:
            return false
        default:
            return true
        }
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch wheelManager.connectionState {
        case .connecting, .discoveringServices:
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(0.8)
        case .connected:
            Image(systemName: "checkmark.circle.fill")
        case .connectionLost:
            Image(systemName: "exclamationmark.triangle.fill")
        case .failed:
            Image(systemName: "xmark.circle.fill")
        default:
            EmptyView()
        }
    }

    private var bannerColor: Color {
        switch wheelManager.connectionState {
        case .connecting, .discoveringServices:
            return .blue
        case .connected:
            return .green
        case .connectionLost:
            return .orange
        case .failed:
            return .red
        default:
            return .gray
        }
    }
}

#Preview {
    VStack {
        ConnectionBanner()
            .environmentObject(WheelManager())
        Spacer()
    }
}
