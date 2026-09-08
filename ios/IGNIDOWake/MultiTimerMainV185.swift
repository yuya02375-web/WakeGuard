import SwiftUI

/// v1.8.5 timer screen.
/// Keeps an always-visible direct h:m:s input on the main screen while retaining
/// a separate Add flow for names, media and saved timers.
struct MultiTimerViewV185: View {
    @EnvironmentObject private var store: MultiTimerStore

    @State private var adding = false
    @State private var editing: WakeTimerItem?
    @State private var enlarged: WakeTimerItem?

    @State private var hoursText = "0"
    @State private var minutesText = "5"
    @State private var secondsText = "0"

    @AppStorage("ignido.timer.displayMode") private var displayMode = 0

    private let quickMinutes = [1, 3, 5, 10, 15, 30]
    private let maximumSeconds = 99 * 3600 + 59 * 60 + 59

    var body: some View {
        NavigationStack {
            ZStack {
                IgnidoScreenBackground()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        header
                        quickSection
                        directInputSection
                        timersSection
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 12)
                    .padding(.bottom, 28)
                }
            }
            // The previous large navigation title inherited a dark foreground on
            // the black IGNIDO background. Hide the system navigation title and
            // render the title explicitly above so it can never blend into it.
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $adding) {
                MultiTimerCreateView()
                    .preferredColorScheme(.dark)
            }
            .sheet(item: $editing) { item in
                NavigationStack { MultiTimerEditor(item: item) }
                    .preferredColorScheme(.dark)
            }
            .fullScreenCover(item: $enlarged) { item in
                MultiTimerExpandedV185(timerID: item.id, displayMode: $displayMode)
            }
        }
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        HStack(alignment: .center) {
            Text("タイマー")
                .font(.system(size: 34, weight: .bold, design: .default))
                .foregroundStyle(Color.white)

            Spacer()

            Button {
                adding = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 25, weight: .regular))
                    .foregroundStyle(IgnidoTheme.ember)
                    .frame(width: 52, height: 52)
                    .background(Color.white.opacity(0.08), in: Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
            }
            .accessibilityLabel("タイマーを追加")
        }
    }

    private var quickSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("すぐ使う")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(quickMinutes, id: \.self) { minutes in
                        Button("\(minutes)分") {
                            Task {
                                _ = await store.createAndStart(
                                    label: "",
                                    duration: TimeInterval(minutes * 60),
                                    saved: false
                                )
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(IgnidoTheme.ember.opacity(0.78))
                        .foregroundStyle(Color.white)
                    }
                }
                .padding(.trailing, 8)
            }
        }
    }

    private var directInputSection: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack {
                sectionTitle("時間を入力")
                Spacer()
                Text("最大 99:59:59")
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.55))
            }

            HStack(spacing: 10) {
                directField("時", text: $hoursText)
                Text(":")
                    .font(.title2.bold())
                    .foregroundStyle(Color.white.opacity(0.55))
                directField("分", text: $minutesText)
                Text(":")
                    .font(.title2.bold())
                    .foregroundStyle(Color.white.opacity(0.55))
                directField("秒", text: $secondsText)
            }

            HStack(spacing: 10) {
                Button {
                    startFromInput()
                } label: {
                    Label("開始", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(IgnidoTheme.ember)
                .foregroundStyle(Color.white)
                .disabled(totalSeconds <= 0)

                Button {
                    addFromInput()
                } label: {
                    Label("追加", systemImage: "plus")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(IgnidoTheme.ember)
                .foregroundStyle(Color.white)
                .disabled(totalSeconds <= 0)
            }

            Button {
                adding = true
            } label: {
                Label("詳細を設定して追加", systemImage: "slider.horizontal.3")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(IgnidoTheme.ember)
            }

            Text("「開始」は今回だけすぐ開始。「追加」は入力した時間をタイマー一覧へ保存します。右上の＋では名前・音/動画なども設定できます。")
                .font(.caption)
                .foregroundStyle(Color.white.opacity(0.60))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .background(Color.white.opacity(0.045), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var timersSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("タイマー")

            if store.items.isEmpty {
                VStack(spacing: 10) {
                    IgnidoFlameMark()
                        .frame(width: 38, height: 52)
                    Text("タイマーなし")
                        .font(.headline)
                        .foregroundStyle(Color.white)
                    Text("上で時間を直接入力するか、右上の＋から追加できます。")
                        .font(.caption)
                        .foregroundStyle(Color.white.opacity(0.62))
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 30)
                .padding(.horizontal, 16)
                .background(Color.white.opacity(0.025), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
            } else {
                LazyVStack(spacing: 12) {
                    ForEach(store.items) { item in
                        TimerRowV185(
                            item: item,
                            displayMode: $displayMode,
                            onEdit: { editing = item },
                            onEnlarge: { enlarged = item }
                        )
                    }
                }
            }
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.title3.bold())
            .foregroundStyle(Color.white.opacity(0.78))
    }

    private func directField(_ label: String, text: Binding<String>) -> some View {
        VStack(spacing: 6) {
            TextField("0", text: text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(.system(size: 27, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(Color.white)
                .tint(IgnidoTheme.ember)
                .padding(.vertical, 11)
                .padding(.horizontal, 6)
                .background(Color.white.opacity(0.075), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(IgnidoTheme.ember.opacity(0.48), lineWidth: 1)
                )
                .onChange(of: text.wrappedValue) { _, _ in
                    normalizeOverflowIfNeeded()
                }

            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.58))
        }
        .frame(maxWidth: .infinity)
    }

    private var totalSeconds: Int {
        let h = max(0, Int(hoursText) ?? 0)
        let m = max(0, Int(minutesText) ?? 0)
        let s = max(0, Int(secondsText) ?? 0)
        return min(maximumSeconds, h * 3600 + m * 60 + s)
    }

    private func normalizeOverflowIfNeeded() {
        let h = max(0, Int(hoursText) ?? 0)
        let m = max(0, Int(minutesText) ?? 0)
        let s = max(0, Int(secondsText) ?? 0)
        if h > 99 || m >= 60 || s >= 60 {
            setTotalSeconds(min(maximumSeconds, h * 3600 + m * 60 + s))
        }
    }

    private func setTotalSeconds(_ seconds: Int) {
        let value = max(0, min(maximumSeconds, seconds))
        hoursText = String(value / 3600)
        minutesText = String((value % 3600) / 60)
        secondsText = String(value % 60)
    }

    private func startFromInput() {
        normalizeOverflowIfNeeded()
        let duration = TimeInterval(totalSeconds)
        guard duration > 0 else { return }
        Task {
            _ = await store.createAndStart(label: "", duration: duration, saved: false)
        }
    }

    private func addFromInput() {
        normalizeOverflowIfNeeded()
        let duration = TimeInterval(totalSeconds)
        guard duration > 0 else { return }
        _ = store.add(label: "", duration: duration, saved: true)
    }
}

private struct TimerRowV185: View {
    @EnvironmentObject private var store: MultiTimerStore
    let item: WakeTimerItem
    @Binding var displayMode: Int
    let onEdit: () -> Void
    let onEnlarge: () -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.2)) { _ in
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 5) {
                        Group {
                            if displayMode == 0 {
                                Text(formatDuration(item.currentRemaining))
                                    .font(.system(size: 38, weight: .light, design: .rounded))
                                    .monospacedDigit()
                                    .foregroundStyle(Color.white)
                            } else {
                                IgnidoProgressFace(
                                    remaining: item.currentRemaining,
                                    total: max(item.duration, 1)
                                )
                                .frame(width: 96, height: 96)
                            }
                        }
                        .contentShape(Rectangle())
                        .gesture(
                            LongPressGesture(minimumDuration: 0.45)
                                .exclusively(before: TapGesture())
                                .onEnded { value in
                                    switch value {
                                    case .first:
                                        onEnlarge()
                                    case .second:
                                        displayMode = displayMode == 0 ? 1 : 0
                                    }
                                }
                        )

                        Text(item.label.isEmpty ? "タイマー" : item.label)
                            .font(.headline)
                            .foregroundStyle(Color.white)

                        if item.running, let endDate = item.endDate {
                            Text("終了予定 \(endDate.formatted(date: .omitted, time: .shortened))")
                                .font(.caption)
                                .foregroundStyle(Color.white.opacity(0.58))
                        } else if !item.saved {
                            Text("今回だけ")
                                .font(.caption)
                                .foregroundStyle(Color.white.opacity(0.58))
                        }
                    }

                    Spacer(minLength: 6)

                    Button(action: onEdit) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.title3)
                            .foregroundStyle(IgnidoTheme.ember)
                            .frame(width: 38, height: 38)
                            .background(Color.white.opacity(0.06), in: Circle())
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 9) {
                        if item.running {
                            Button("一時停止") { store.pause(item) }
                                .buttonStyle(.borderedProminent)
                                .tint(IgnidoTheme.ember)
                        } else {
                            Button("開始") { Task { await store.start(item) } }
                                .buttonStyle(.borderedProminent)
                                .tint(IgnidoTheme.ember)
                        }

                        Button("+1分") {
                            Task { await store.addTime(item, seconds: 60) }
                        }
                        .buttonStyle(.bordered)
                        .tint(IgnidoTheme.ember)

                        Button("リセット") { store.reset(item) }
                            .buttonStyle(.bordered)
                            .tint(IgnidoTheme.ember)

                        if !item.saved {
                            Button("保存") { store.makeSaved(item) }
                                .buttonStyle(.bordered)
                                .tint(IgnidoTheme.ember)
                        }

                        Button(role: .destructive) {
                            store.delete(item)
                        } label: {
                            Label("削除", systemImage: "trash")
                        }
                        .buttonStyle(.bordered)
                    }
                    .foregroundStyle(Color.white)
                }
            }
            .padding(16)
            .background(Color.white.opacity(0.045), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(0.10), lineWidth: 1)
            )
        }
    }
}

private struct MultiTimerExpandedV185: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: MultiTimerStore
    let timerID: UUID
    @Binding var displayMode: Int

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                VStack(spacing: 28) {
                    if let item = store.item(id: timerID) {
                        Group {
                            if displayMode == 0 {
                                Text(formatDuration(item.currentRemaining))
                                    .font(.system(size: 76, weight: .light, design: .rounded))
                                    .monospacedDigit()
                                    .foregroundStyle(Color.white)
                            } else {
                                IgnidoProgressFace(
                                    remaining: item.currentRemaining,
                                    total: max(item.duration, 1)
                                )
                                .frame(width: 330, height: 330)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            displayMode = displayMode == 0 ? 1 : 0
                        }

                        Text(item.label.isEmpty ? "タイマー" : item.label)
                            .font(.headline)
                            .foregroundStyle(Color.white.opacity(0.88))
                    } else {
                        Text("タイマー終了")
                            .font(.largeTitle.bold())
                            .foregroundStyle(Color.white)
                    }

                    Button("閉じる") { dismiss() }
                        .buttonStyle(.borderedProminent)
                        .tint(IgnidoTheme.ember)
                }
                .padding()
            }
        }
    }
}
