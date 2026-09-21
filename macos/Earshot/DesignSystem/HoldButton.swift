import SwiftUI

/// Кнопка срочного вызова: срабатывает только удержанием.
///
/// Не модалка «вы уверены» — её прокликивают не глядя. Удержание делает
/// случайную отправку физически невозможной.
struct HoldButton: View {
    let isEnabled: Bool
    let accessibilityText: String
    let onFire: () -> Void

    @State private var progress: Double = 0
    @State private var timer: Timer?

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.primary.opacity(0.07))
            Circle()
                .trim(from: 0, to: progress)
                .stroke(Tokens.Palette.urgent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text(verbatim: "!")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Tokens.Palette.urgent)
        }
        .frame(width: Tokens.Metric.urgentButton, height: Tokens.Metric.urgentButton)
        .opacity(isEnabled ? 1 : 0.3)
        .contentShape(Circle())
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
        .onLongPressGesture(
            minimumDuration: Tokens.Metric.holdSeconds,
            maximumDistance: 20,
            perform: { fire() },
            onPressingChanged: { pressing in pressing ? begin() : cancel() }
        )
        .disabled(!isEnabled)
    }

    private func begin() {
        guard isEnabled else { return }
        progress = 0
        let step = 0.02
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: step, repeats: true) { _ in
            Task { @MainActor in
                progress = min(1, progress + step / Tokens.Metric.holdSeconds)
            }
        }
    }

    private func cancel() {
        timer?.invalidate()
        timer = nil
        withAnimation(.easeOut(duration: 0.15)) { progress = 0 }
    }

    private func fire() {
        cancel()
        onFire()
    }
}
