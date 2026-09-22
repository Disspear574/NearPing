import SwiftUI

/// Кнопка срочного вызова: срабатывает только удержанием.
///
/// Не модалка «вы уверены» — её прокликивают не глядя. Удержание делает
/// случайную отправку физически невозможной. Кольцо показывает, сколько
/// осталось держать, иначе жест выглядит сломанным.
struct HoldButton: View {
    let isEnabled: Bool
    let accessibilityText: String
    let onFire: () -> Void

    @State private var progress: Double = 0
    @State private var timer: Timer?
    @State private var hovered = false

    var body: some View {
        ZStack {
            Circle()
                .fill(Tokens.Palette.urgent.opacity(hovered && isEnabled ? 0.22 : 0.14))
            Circle()
                .stroke(Tokens.Palette.urgent.opacity(0.45), lineWidth: 1)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(Tokens.Palette.urgent, style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text(verbatim: "!")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Tokens.Palette.urgent)
        }
        .frame(width: Tokens.Metric.urgentButton, height: Tokens.Metric.urgentButton)
        .opacity(isEnabled ? 1 : 0.35)
        .contentShape(Circle())
        .onHover { hovered = $0 }
        .accessibilityLabel(accessibilityText)
        .accessibilityAddTraits(.isButton)
        .onLongPressGesture(
            minimumDuration: Tokens.Metric.holdSeconds,
            maximumDistance: 20,
            perform: { fire() },
            onPressingChanged: { pressing in pressing ? begin() : cancel() }
        )
        .disabled(!isEnabled)
        .onDisappear { cancel() }
        .onChange(of: isEnabled) { _, enabled in if !enabled { cancel() } }
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
        guard isEnabled else { cancel(); return }
        cancel()
        onFire()
    }
}
