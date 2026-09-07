import SwiftUI
import AVKit
import AVFoundation

struct WakeMediaPlayerView: UIViewControllerRepresentable {
    let url: URL
    var volume: Float = 1.0
    var loop = true

    func makeCoordinator() -> Coordinator { Coordinator(loop: loop) }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.volume = volume
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.entersFullScreenWhenPlaybackBegins = false
        controller.exitsFullScreenWhenPlaybackEnds = false
        controller.allowedSubtitleOptionLanguages = nil
        context.coordinator.player = player
        context.coordinator.item = item
        context.coordinator.selectSubtitleIfAvailable()
        context.coordinator.observeEnd()
        player.play()
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.player?.volume = volume
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
                self.player?.play()
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
            Button {
                if alarm.mission == .none { onDismiss() } else { showMission = true }
            } label: {
                Label(alarm.mission == .none ? "停止" : "解除", systemImage: "xmark.circle.fill")
                    .font(.headline).padding(.horizontal, 14).padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule())
            }.padding()
        }
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
    }
}
