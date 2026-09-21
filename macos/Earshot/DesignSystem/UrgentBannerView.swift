import SwiftUI

/// Баннер срочного вызова. Живёт в отдельном окне поверх всего.
struct UrgentBannerView: View {
    let callerName: String
    let totalSeconds: TimeInterval
    let onSeen: () -> Void

    @State private var remaining: Double = 1

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Circle()
                    .fill(Color.primary.opacity(0.08))
                    .overlay(Circle().stroke(Tokens.Palette.urgent, lineWidth: 2.5))
                    .overlay(
                        Text(initials)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Tokens.Palette.primary)
                    )
                    .frame(width: 46, height: 46)

                VStack(alignment: .leading, spacing: 3) {
                    // Имя задаёт человек: длину не ограничиваем, но и раскладку ломать не даём.
                    Text(String(localized: "\(callerName) is calling"))
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(Tokens.Palette.primary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(String(localized: "urgent"))
                        .font(Tokens.Font.caption)
                        .foregroundStyle(Tokens.Palette.urgent)
                        .lineLimit(1)
                }
                .layoutPriority(1)

                Spacer(minLength: 8)

                Button(action: onSeen) {
                    Text(String(localized: "Got it"))
                        .font(.system(size: 13, weight: .semibold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                }
                .buttonStyle(.borderedProminent)
                .fixedSize()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)

            GeometryReader { geometry in
                Rectangle()
                    .fill(Tokens.Palette.urgent)
                    .frame(width: geometry.size.width * remaining)
            }
            .frame(height: 3)
        }
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(Color.primary.opacity(0.12), lineWidth: 0.5)
        )
        .onAppear {
            withAnimation(.linear(duration: totalSeconds)) { remaining = 0 }
        }
    }

    private var initials: String {
        let parts = callerName.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "?" : letters.uppercased()
    }
}
