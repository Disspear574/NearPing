import SwiftUI

/// Top banner for an incoming ping; urgent uses red progress, normal the calm forest palette.
struct UrgentBannerView: View {
    let callerName: String
    let isUrgent: Bool
    let totalSeconds: TimeInterval
    let onStop: () -> Void

    @State private var remaining: Double = 1

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Circle()
                    .fill(Color.primary.opacity(0.08))
                    .overlay(Circle().stroke(graphicColor, lineWidth: 2.5))
                    .overlay(
                        Image("NearPingMark").resizable().scaledToFit().frame(width: 28, height: 28)
                            .foregroundStyle(Tokens.Palette.primary)
                    )
                    .frame(width: 46, height: 46)

                VStack(alignment: .leading, spacing: 3) {
                    // The name is set by the person: length is unbounded but must not break the layout.
                    Text(String(localized: "\(callerName) is calling"))
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(Tokens.Palette.primary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Text(isUrgent ? String(localized: "urgent") : String(localized: "Incoming Ping"))
                        .font(Tokens.Font.caption)
                        .foregroundStyle(bannerColor)
                        .lineLimit(1)
                }
                .layoutPriority(1)

                Spacer(minLength: 8)

                Button(action: onStop) {
                    Text(String(localized: "Stop"))
                        .font(.system(size: 13, weight: .semibold))
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                }
                .buttonStyle(.borderedProminent)
                .tint(isUrgent ? Tokens.Palette.urgentFill : Tokens.Palette.accent)
                .fixedSize()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)

            GeometryReader { geometry in
                Rectangle()
                    .fill(graphicColor)
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

    // The urgent label uses light red, the bar and ring a saturated one.
    private var bannerColor: Color {
        isUrgent ? Tokens.Palette.urgent : Tokens.Palette.accent
    }

    private var graphicColor: Color {
        isUrgent ? Tokens.Palette.urgentFill : Tokens.Palette.accent
    }

}
