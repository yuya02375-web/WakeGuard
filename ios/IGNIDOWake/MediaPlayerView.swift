import SwiftUI
import AVKit
import AVFoundation

struct WakeMediaPlayerView: UIViewControllerRepresentable {
    let url: URL
    var volume: Float = 1.0
    var loop = true

    func makeCoordinator() -> Coordinator { Coordinator(loop: loop) }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        AlarmRuntime.preparePlaybackSession()
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.volume = min(max(volume, 0), 1)
        player.isMuted = false
        player.appliesMediaSelectionCriteriaAutomatically = true
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.entersFullScreenWhenPlaybackBegins = false
        controller.exitsFullScreenWhenPlaybackEnds = false
        context.coordinator.player = player
        context.coordinator.item = item
        context.coordinator.selectSubtitleIfAvailable()
        context.coordinator.observeEnd()
        player.playImmediately(atRate: 1.0)
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        AlarmRuntime.preparePlaybackSession()
        uiViewController.player?.isMuted = false
        uiViewController.player?.volume = min(max(volume, 0), 1)
        if uiViewController.player?.timeControlStatus != .playing {
            uiViewController.player?.play()
        }
    }

    static func dismantleUIViewController(_ uiViewController: AVPlayerViewController, coordinator: Coordinator) {
        coordinator.stop()
        uiViewController.player?.pause()
    }

    final class Coordinator: NSObject {
        let loop: Bool
        weak var player: AVPlayer?
        weak var item: AVPlayerItem?
        private var token: NSObjectProtocol?
        init(loop: Bool) { self.loop = loop }

        @MainActor
        func selectSubtitleIfAvailable() {
            guard let item,
                  let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: .legible),
                  let option = group.defaultOption ?? group.options.first else { return }
            item.select(option, in: group)
        }

        func observeEnd() {
            guard let item else { return }
            token = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                guard let self, self.loop else { return }
                self.player?.seek(to: .zero)
                self.player?.playImmediately(atRate: 1.0)
            }
        }
        func stop() {
            if let token { NotificationCenter.default.removeObserver(token) }
            token = nil
        }
    }
}

struct MediaAlarmScreen: View {
    let alarm: WakeAlarm
    let url: URL
    let onDismiss: () -> Void
    @State private var showMission = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            WakeMediaPlayerView(url: url, volume: Float(alarm.volume), loop: true).ignoresSafeArea()
            VStack(alignment: .trailing, spacing: 10) {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(alarm.timeText)
                        .font(.system(size: 28, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                    Text(alarm.label.isEmpty ? "アラーム" : alarm.label)
                        .font(.headline)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(.black.opacity(0.58), in: RoundedRectangle(cornerRadius: 12))

                Button {
                    if alarm.mission == .none { onDismiss() } else { showMission = true }
                } label: {
                    Label(alarm.mission == .none ? "停止" : "解除", systemImage: "xmark.circle.fill")
                        .font(.headline).padding(.horizontal, 14).padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                }
            }
            .padding()
        }
        .onAppear {
            AlarmRuntime.preparePlaybackSession()
            AlarmHaptics.shared.start(alarm.vibration)
        }
        .onDisappear { AlarmHaptics.shared.stop() }
        .fullScreenCover(isPresented: $showMission) {
            MissionView(alarm: alarm) { showMission = false; onDismiss() }
        }
    }
}

struct TimerMediaScreen: View {
    let url: URL
    let onDismiss: () -> Void
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            WakeMediaPlayerView(url: url, loop: true).ignoresSafeArea()
            Button(action: onDismiss) {
                Label("タイマーを停止", systemImage: "xmark.circle.fill")
                    .font(.headline).padding(.horizontal, 14).padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule())
            }.padding()
        }
        .onAppear { AlarmRuntime.preparePlaybackSession() }
    }
}
