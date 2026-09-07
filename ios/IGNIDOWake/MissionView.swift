import SwiftUI
import CoreMotion

struct MissionView: View {
    let alarm: WakeAlarm
    let onComplete: () -> Void
    @State private var effectiveMission: AlarmMission = .none

    var body: some View {
        VStack(spacing: 24) {
            Text(alarm.label.isEmpty ? "アラーム" : alarm.label).font(.title.bold())
            Text(alarm.timeText).font(.system(size: 58, weight: .light, design: .rounded)).monospacedDigit()
            Text("解除方法: \(effectiveMission.title)").foregroundStyle(IgnidoTheme.secondaryText)
            Group {
                switch effectiveMission {
                case .none: Button("停止") { finish() }.buttonStyle(.borderedProminent)
                case .steps: StepMissionView(target: max(1, alarm.missionTarget), onComplete: finish)
                case .math: MathMissionView(target: max(1, alarm.missionTarget), onComplete: finish)
                case .taps: TapMissionView(target: max(1, alarm.missionTarget), onComplete: finish)
                case .code: CodeMissionView(code: alarm.unlockCode, onComplete: finish)
                case .shake: ShakeMissionView(target: max(1, alarm.missionTarget), onComplete: finish)
                case .memory: MemoryMissionView(onComplete: finish)
                case .sentence: SentenceMissionView(sentence: alarm.unlockSentence, onComplete: finish)
                case .hold: HoldMissionView(seconds: max(1, alarm.missionTarget), onComplete: finish)
                case .swipe: SwipeMissionView(target: max(1, alarm.missionTarget), onComplete: finish)
                case .order: OrderMissionView(onComplete: finish)
                case .reverse: ReverseMissionView(onComplete: finish)
                case .random: EmptyView()
                }
            }
            Spacer()
        }
        .padding(24)
        .background(IgnidoTheme.background.ignoresSafeArea())
        .foregroundStyle(IgnidoTheme.text)
        .tint(IgnidoTheme.ember)
        .onAppear {
            effectiveMission = alarm.mission == .random ? (AlarmMission.randomCandidates.randomElement() ?? .math) : alarm.mission
            AlarmRuntime.preparePlaybackSession()
            AlarmHaptics.shared.start(alarm.vibration)
        }
        .onDisappear { AlarmHaptics.shared.stop() }
    }

    private func finish() {
        AlarmHaptics.shared.stop()
        onComplete()
    }
}

private struct ProgressLabel: View {
    let value: Int, target: Int, title: String
    var body: some View {
        VStack(spacing: 8) {
            Text(title).font(.headline)
            Text("\(value) / \(target)").font(.system(size: 34, weight: .semibold, design: .rounded)).monospacedDigit()
            ProgressView(value: Double(value), total: Double(max(target, 1))).tint(IgnidoTheme.ember)
        }
    }
}

struct StepMissionView: View {
    let target: Int
    let onComplete: () -> Void
    @State private var steps = 0
    @State private var errorText: String?
    private let pedometer = CMPedometer()

    var body: some View {
        VStack(spacing: 18) {
            ProgressLabel(value: steps, target: target, title: "スマホを持って歩く")
            Text("iPhoneのモーションプロセッサが認識した実歩数だけを使います。振っただけの加速度は直接歩数として加算しません。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText).multilineTextAlignment(.center)
            if let errorText { Text(errorText).font(.caption).foregroundStyle(.red) }
        }
        .onAppear { start() }
        .onDisappear { pedometer.stopUpdates() }
    }
    private func start() {
        guard CMPedometer.isStepCountingAvailable() else { errorText = "この端末では歩数計を利用できません"; return }
        let start = Date()
        pedometer.startUpdates(from: start) { data, error in
            Task { @MainActor in
                if let error { errorText = error.localizedDescription; return }
                let count = data?.numberOfSteps.intValue ?? 0
                steps = max(steps, count)
                if steps >= target { pedometer.stopUpdates(); onComplete() }
            }
        }
    }
}

struct MathMissionView: View {
    let target: Int
    let onComplete: () -> Void
    @State private var solved = 0
    @State private var a = 0
    @State private var b = 0
    @State private var op = 0
    @State private var answer = ""
    @State private var wrong = false

    var body: some View {
        VStack(spacing: 16) {
            ProgressLabel(value: solved, target: target, title: "計算問題")
            Text(question).font(.system(size: 38, weight: .bold, design: .rounded))
            TextField("答え", text: $answer).keyboardType(.numbersAndPunctuation).textFieldStyle(.roundedBorder)
            Button("回答") { submit() }.buttonStyle(.borderedProminent)
            if wrong { Text("違います").foregroundStyle(.red) }
        }.onAppear { next() }
    }
    private var correct: Int { op == 0 ? a + b : (op == 1 ? a - b : a * b) }
    private var question: String { "\(a) \(["+","−","×"][op]) \(b) = ?" }
    private func next() {
        op = Int.random(in: 0...2); a = Int.random(in: op == 2 ? 2...12 : 10...99); b = Int.random(in: op == 2 ? 2...12 : 1...49)
        if op == 1 && b > a { swap(&a, &b) }
        answer = ""; wrong = false
    }
    private func submit() {
        guard Int(answer) == correct else { wrong = true; return }
        solved += 1
        if solved >= target { onComplete() } else { next() }
    }
}

struct TapMissionView: View {
    let target: Int
    let onComplete: () -> Void
    @State private var count = 0
    var body: some View {
        VStack(spacing: 20) {
            ProgressLabel(value: count, target: target, title: "連打")
            Button("TAP") { count += 1; if count >= target { onComplete() } }
                .font(.title.bold()).frame(maxWidth: .infinity, minHeight: 120).buttonStyle(.borderedProminent)
        }
    }
}

struct CodeMissionView: View {
    let code: String
    let onComplete: () -> Void
    @State private var input = ""
    @State private var wrong = false
    var body: some View {
        VStack(spacing: 16) {
            Text("設定したコードを入力").font(.headline)
            TextField("コード", text: $input).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder)
            Button("解除") { if input == code { onComplete() } else { wrong = true } }.buttonStyle(.borderedProminent)
            if wrong { Text("コードが違います").foregroundStyle(.red) }
        }
    }
}

struct ShakeMissionView: View {
    let target: Int
    let onComplete: () -> Void
    @State private var count = 0
    @State private var lastShake = Date.distantPast
    private let manager = CMMotionManager()

    var body: some View {
        VStack(spacing: 18) {
            ProgressLabel(value: count, target: target, title: "スマホをしっかり振る")
            Text("連続した微振動を多重カウントしないよう、1回ごとに間隔を置いて判定します。")
                .font(.caption).foregroundStyle(IgnidoTheme.secondaryText).multilineTextAlignment(.center)
        }.onAppear { start() }.onDisappear { manager.stopDeviceMotionUpdates() }
    }
    private func start() {
        guard manager.isDeviceMotionAvailable else { return }
        manager.deviceMotionUpdateInterval = 1.0 / 30.0
        manager.startDeviceMotionUpdates(to: .main) { motion, _ in
            guard let u = motion?.userAcceleration else { return }
            let magnitude = sqrt(u.x*u.x + u.y*u.y + u.z*u.z)
            let now = Date()
            guard magnitude > 1.65, now.timeIntervalSince(lastShake) > 0.38 else { return }
            lastShake = now; count += 1
            if count >= target { manager.stopDeviceMotionUpdates(); onComplete() }
        }
    }
}

struct MemoryMissionView: View {
    let onComplete: () -> Void
    @State private var sequence: [Int] = []
    @State private var input: [Int] = []
    @State private var showing = true
    @State private var wrong = false
    var body: some View {
        VStack(spacing: 18) {
            Text(showing ? "順番を覚える" : "同じ順番で押す").font(.headline)
            if showing {
                Text(sequence.map(String.init).joined(separator: "  ")).font(.system(size: 36, weight: .bold, design: .rounded))
            } else {
                HStack { ForEach(1...6, id: \.self) { n in Button("\(n)") { tap(n) }.buttonStyle(.borderedProminent) } }
            }
            if wrong { Text("違います。もう一度").foregroundStyle(.red) }
        }.onAppear { reset() }
    }
    private func reset() {
        sequence = Array(1...6).shuffled().prefix(4).map { $0 }; input = []; wrong = false; showing = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { showing = false }
    }
    private func tap(_ n: Int) {
        input.append(n)
        let idx = input.count - 1
        guard idx < sequence.count, input[idx] == sequence[idx] else { wrong = true; input = []; return }
        if input.count == sequence.count { onComplete() }
    }
}

struct SentenceMissionView: View {
    let sentence: String
    let onComplete: () -> Void
    @State private var input = ""
    var body: some View {
        VStack(spacing: 14) {
            Text("次の文章をそのまま入力").font(.headline)
            Text(sentence).padding().frame(maxWidth: .infinity).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
            TextField("文章", text: $input).textFieldStyle(.roundedBorder)
            Button("解除") { if input == sentence { onComplete() } }.buttonStyle(.borderedProminent)
        }
    }
}

struct HoldMissionView: View {
    let seconds: Int
    let onComplete: () -> Void
    @State private var pressing = false
    var body: some View {
        VStack(spacing: 16) {
            Text("\(seconds)秒間、離さず長押し").font(.headline)
            RoundedRectangle(cornerRadius: 28).fill(pressing ? IgnidoTheme.ember.opacity(0.8) : Color.secondary.opacity(0.25))
                .frame(height: 150).overlay(Text(pressing ? "そのまま" : "長押し").font(.title.bold()))
                .onLongPressGesture(minimumDuration: Double(seconds), maximumDistance: 40, pressing: { pressing = $0 }, perform: onComplete)
        }
    }
}

struct SwipeMissionView: View {
    let target: Int
    let onComplete: () -> Void
    @State private var count = 0
    var body: some View {
        VStack(spacing: 18) {
            ProgressLabel(value: count, target: target, title: "大きくスワイプ")
            RoundedRectangle(cornerRadius: 24).fill(Color.secondary.opacity(0.2)).frame(height: 180)
                .overlay(Image(systemName: "arrow.left.and.right").font(.system(size: 52)))
                .gesture(DragGesture(minimumDistance: 70).onEnded { _ in count += 1; if count >= target { onComplete() } })
        }
    }
}

struct OrderMissionView: View {
    let onComplete: () -> Void
    @State private var order = Array(1...9).shuffled()
    @State private var next = 1
    var body: some View {
        VStack(spacing: 16) {
            Text("1 → 9 の順に押す").font(.headline)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3)) {
                ForEach(order, id: \.self) { n in
                    Button("\(n)") { tap(n) }.font(.title.bold()).frame(maxWidth: .infinity, minHeight: 64).buttonStyle(.bordered)
                }
            }
        }
    }
    private func tap(_ n: Int) {
        if n == next { next += 1; if next == 10 { onComplete() } }
        else { next = 1; order.shuffle() }
    }
}

struct ReverseMissionView: View {
    let onComplete: () -> Void
    @State private var source = ""
    @State private var input = ""
    var body: some View {
        VStack(spacing: 16) {
            Text("逆順に入力").font(.headline)
            Text(source).font(.system(size: 36, weight: .bold, design: .monospaced))
            TextField("逆から入力", text: $input).textInputAutocapitalization(.never).autocorrectionDisabled().textFieldStyle(.roundedBorder)
            Button("解除") { if input == String(source.reversed()) { onComplete() } }.buttonStyle(.borderedProminent)
        }.onAppear { source = String((0..<6).map { _ in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".randomElement()! }) }
    }
}
