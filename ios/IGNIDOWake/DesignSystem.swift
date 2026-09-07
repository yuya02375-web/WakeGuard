import SwiftUI
import UIKit

/// Shared visual language for IGNIDO Wake.
/// Dark graphite base + ember red/orange accents. The accent communicates action;
/// neutral surfaces stay quiet so time and alarm state remain the strongest signals.
enum IgnidoTheme {
    static let background = Color(red: 0.031, green: 0.039, blue: 0.055)
    static let background2 = Color(red: 0.047, green: 0.059, blue: 0.082)
    static let surface = Color(red: 0.071, green: 0.086, blue: 0.114)
    static let surface2 = Color(red: 0.094, green: 0.114, blue: 0.149)
    static let border = Color(red: 0.173, green: 0.204, blue: 0.259)
    static let ember = Color(red: 1.0, green: 0.294, blue: 0.169)
    static let amber = Color(red: 1.0, green: 0.604, blue: 0.235)
    static let text = Color(red: 0.969, green: 0.969, blue: 0.973)
    static let muted = Color(red: 0.659, green: 0.686, blue: 0.729)
    static let chrome = Color(red: 0.039, green: 0.047, blue: 0.063)

    static let emberGradient = LinearGradient(
        colors: [ember, Color(red: 1.0, green: 0.39, blue: 0.20), amber],
        startPoint: .bottomLeading,
        endPoint: .topTrailing
    )

    static let screenGradient = LinearGradient(
        colors: [background, background2, Color(red: 0.063, green: 0.078, blue: 0.106)],
        startPoint: .top,
        endPoint: .bottom
    )
}

struct IgnidoScreenBackground: View {
    var body: some View {
        ZStack(alignment: .topTrailing) {
            IgnidoTheme.screenGradient
            RadialGradient(
                colors: [IgnidoTheme.ember.opacity(0.10), .clear],
                center: .topTrailing,
                startRadius: 0,
                endRadius: 280
            )
        }
        .ignoresSafeArea()
    }
}

struct IgnidoCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(IgnidoTheme.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .stroke(IgnidoTheme.border.opacity(0.85), lineWidth: 1)
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

        let background = UIColor(red: 0.031, green: 0.039, blue: 0.055, alpha: 1)
        let chrome = UIColor(red: 0.039, green: 0.047, blue: 0.063, alpha: 0.98)
        let ember = UIColor(red: 1.0, green: 0.294, blue: 0.169, alpha: 1)
        let muted = UIColor(red: 0.659, green: 0.686, blue: 0.729, alpha: 1)

        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = chrome
        nav.shadowColor = .clear
        nav.titleTextAttributes = [.foregroundColor: UIColor.white]
        nav.largeTitleTextAttributes = [.foregroundColor: UIColor.white]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav
        UINavigationBar.appearance().tintColor = ember

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = chrome
        tab.shadowColor = UIColor.white.withAlphaComponent(0.05)
        tab.stackedLayoutAppearance.normal.iconColor = muted
        tab.stackedLayoutAppearance.normal.titleTextAttributes = [.foregroundColor: muted]
        tab.stackedLayoutAppearance.selected.iconColor = ember
        tab.stackedLayoutAppearance.selected.titleTextAttributes = [.foregroundColor: ember]
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        UISwitch.appearance().onTintColor = ember
        UISlider.appearance().minimumTrackTintColor = ember
        UITableView.appearance().backgroundColor = background
        UICollectionView.appearance().backgroundColor = background
    }
}
