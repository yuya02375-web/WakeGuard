import SwiftUI
import UIKit

/// IGNIDO Wake visual system.
/// Warm soot-black surfaces + concentrated flame color. Fire appears as a mark,
/// edge, or active-state signal instead of a generic glow around every component.
enum IgnidoTheme {
    static let background = Color(red: 0.031, green: 0.027, blue: 0.024)
    static let background2 = Color(red: 0.051, green: 0.043, blue: 0.035)
    static let surface = Color(red: 0.094, green: 0.076, blue: 0.059)
    static let surface2 = Color(red: 0.126, green: 0.098, blue: 0.070)
    static let border = Color(red: 0.340, green: 0.235, blue: 0.158)
    static let ember = Color(red: 1.0, green: 0.227, blue: 0.078)
    static let flame = Color(red: 1.0, green: 0.478, blue: 0.0)
    static let amber = flame
    static let hot = Color(red: 1.0, green: 0.784, blue: 0.341)
    static let text = Color(red: 1.0, green: 0.973, blue: 0.941)
    /// Secondary text is intentionally brighter than the old muted tone so labels
    /// stay readable on the soot-black UI, including disabled alarm rows.
    static let muted = Color(red: 0.815, green: 0.768, blue: 0.724)
    static let secondaryText = muted
    static let chrome = Color(red: 0.055, green: 0.045, blue: 0.035)

    /// Reserved for flame artwork. Regular buttons and cards should stay solid.
    static let flameGradient = LinearGradient(
        colors: [hot, flame, ember],
        startPoint: .bottom,
        endPoint: .top
    )

    static let emberGradient = flameGradient

    static let screenGradient = LinearGradient(
        colors: [background, Color(red: 0.043, green: 0.034, blue: 0.026), background2],
        startPoint: .top,
        endPoint: .bottom
    )
}

struct IgnidoScreenBackground: View {
    var body: some View {
        IgnidoTheme.screenGradient
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(IgnidoTheme.ember.opacity(0.22))
                    .frame(height: 1)
            }
            .ignoresSafeArea()
    }
}

struct IgnidoFlameShape: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w * 0.53, y: h * 0.02))
        p.addCurve(
            to: CGPoint(x: w * 0.22, y: h * 0.50),
            control1: CGPoint(x: w * 0.52, y: h * 0.22),
            control2: CGPoint(x: w * 0.31, y: h * 0.24)
        )
        p.addCurve(
            to: CGPoint(x: w * 0.08, y: h * 0.72),
            control1: CGPoint(x: w * 0.14, y: h * 0.57),
            control2: CGPoint(x: w * 0.08, y: h * 0.65)
        )
        p.addCurve(
            to: CGPoint(x: w * 0.50, y: h * 0.99),
            control1: CGPoint(x: w * 0.08, y: h * 0.89),
            control2: CGPoint(x: w * 0.28, y: h * 0.99)
        )
        p.addCurve(
            to: CGPoint(x: w * 0.92, y: h * 0.70),
            control1: CGPoint(x: w * 0.74, y: h * 0.99),
            control2: CGPoint(x: w * 0.92, y: h * 0.86)
        )
        p.addCurve(
            to: CGPoint(x: w * 0.70, y: h * 0.31),
            control1: CGPoint(x: w * 0.91, y: h * 0.56),
            control2: CGPoint(x: w * 0.79, y: h * 0.48)
        )
        p.addCurve(
            to: CGPoint(x: w * 0.53, y: h * 0.02),
            control1: CGPoint(x: w * 0.62, y: h * 0.20),
            control2: CGPoint(x: w * 0.56, y: h * 0.10)
        )
        p.closeSubpath()
        return p
    }
}

struct IgnidoBurnRail: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 0))
        p.addLine(to: CGPoint(x: w * 0.42, y: 0))
        p.addLine(to: CGPoint(x: w * 0.42, y: h * 0.18))
        p.addLine(to: CGPoint(x: w, y: h * 0.28))
        p.addLine(to: CGPoint(x: w * 0.42, y: h * 0.37))
        p.addLine(to: CGPoint(x: w * 0.42, y: h))
        p.addLine(to: CGPoint(x: 0, y: h))
        p.closeSubpath()
        return p
    }
}

struct IgnidoFlameMark: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            ZStack {
                IgnidoFlameShape()
                    .fill(IgnidoTheme.flameGradient)
                IgnidoFlameShape()
                    .fill(IgnidoTheme.hot.opacity(0.95))
                    .frame(width: w * 0.46, height: h * 0.50)
                    .offset(x: w * 0.02, y: h * 0.23)
            }
        }
        .accessibilityHidden(true)
    }
}

struct IgnidoCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(IgnidoTheme.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(IgnidoTheme.border.opacity(0.78), lineWidth: 0.9)
                    )
            )
    }
}

extension View {
    func ignidoCard() -> some View { modifier(IgnidoCardModifier()) }
}

@MainActor
enum IgnidoAppearance {
    private static var configured = false

    static func configure() {
        guard !configured else { return }
        configured = true

        let background = UIColor(red: 0.031, green: 0.027, blue: 0.024, alpha: 1)
        let chrome = UIColor(red: 0.055, green: 0.045, blue: 0.035, alpha: 0.99)
        let flame = UIColor(red: 1.0, green: 0.478, blue: 0.0, alpha: 1)
        let muted = UIColor(red: 0.815, green: 0.768, blue: 0.724, alpha: 1)

        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = chrome
        nav.shadowColor = UIColor.white.withAlphaComponent(0.08)
        nav.titleTextAttributes = [.foregroundColor: UIColor(red: 1.0, green: 0.973, blue: 0.941, alpha: 1)]
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor(red: 1.0, green: 0.973, blue: 0.941, alpha: 1)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        UINavigationBar.appearance().tintColor = flame

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = chrome
        tab.shadowColor = UIColor.white.withAlphaComponent(0.08)
        tab.stackedLayoutAppearance.normal.iconColor = muted
        tab.stackedLayoutAppearance.normal.titleTextAttributes = [.foregroundColor: muted]
        tab.stackedLayoutAppearance.selected.iconColor = flame
        tab.stackedLayoutAppearance.selected.titleTextAttributes = [.foregroundColor: flame]
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        UISwitch.appearance().onTintColor = flame
        UISlider.appearance().minimumTrackTintColor = flame
        UITableView.appearance().backgroundColor = background
        UICollectionView.appearance().backgroundColor = background
    }
}
