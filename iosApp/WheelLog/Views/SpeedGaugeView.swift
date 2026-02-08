import SwiftUI

struct SpeedGaugeView: View {
    let speed: Double
    let maxSpeed: Double

    init(speed: Double, maxSpeed: Double = 50.0) {
        self.speed = speed
        self.maxSpeed = maxSpeed
    }

    private var speedProgress: Double {
        min(speed / maxSpeed, 1.0)
    }

    private var speedColor: Color {
        if speedProgress < 0.5 { return .green }
        if speedProgress < 0.75 { return .orange }
        return .red
    }

    var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            let lineWidth: CGFloat = size * 0.08
            let radius = (size - lineWidth) / 2

            ZStack {
                // Background arc
                Circle()
                    .trim(from: 0.15, to: 0.85)
                    .stroke(
                        Color.gray.opacity(0.2),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                    )
                    .rotationEffect(.degrees(90))

                // Progress arc
                Circle()
                    .trim(from: 0.15, to: 0.15 + (0.7 * speedProgress))
                    .stroke(
                        speedColor,
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                    )
                    .rotationEffect(.degrees(90))
                    .animation(.spring(response: 0.3), value: speedProgress)

                // Speed labels around the arc
                ForEach(Array(stride(from: 0, through: Int(maxSpeed), by: 10)), id: \.self) { tickSpeed in
                    let tickProgress = Double(tickSpeed) / maxSpeed
                    let angle = Angle.degrees(-144 + (288 * tickProgress))

                    Text("\(tickSpeed)")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .position(
                            x: size / 2 + (radius - lineWidth - 10) * cos(CGFloat(angle.radians - .pi / 2)),
                            y: size / 2 + (radius - lineWidth - 10) * sin(CGFloat(angle.radians - .pi / 2))
                        )
                }

                // Center content
                VStack(spacing: 4) {
                    Text(String(format: "%.1f", speed))
                        .font(.system(size: size * 0.18, weight: .bold, design: .rounded))
                        .foregroundColor(speedColor)

                    Text("km/h")
                        .font(.system(size: size * 0.06))
                        .foregroundColor(.secondary)
                }
            }
            .frame(width: size, height: size)
            .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
        }
    }
}

#Preview {
    VStack(spacing: 40) {
        SpeedGaugeView(speed: 0)
            .frame(height: 200)

        SpeedGaugeView(speed: 25)
            .frame(height: 200)

        SpeedGaugeView(speed: 45)
            .frame(height: 200)
    }
    .padding()
}
