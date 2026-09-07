import SwiftUI

struct ClockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WorldClockStore
    @AppStorage("ignido.clock.analog.numbers") private var showNumbers = true
    @AppStorage("ignido.clock.analog.ticks") private var showMinuteTicks = true
    @AppStorage("ignido.clock.analog.seconds") private var showSecondHand = true
    @AppStorage("ignido.clock.analog.numberScale") private var numberScale = 1
    @AppStorage("ignido.clock.showDetail") private var showDetail = true
    @AppStorage("ignido.clock.keepOn") private var keepOn = false

    var body: some View {
        NavigationStack {
            Form {
                Section("表示") {
                    Toggle("24時間表示", isOn: $store.use24Hour)
                    Toggle("日付・タイムゾーン情報を表示", isOn: $showDetail)
                    Toggle("画面を点灯したままにする", isOn: $keepOn)
                    Picker("初期表示", selection: $store.displayMode) {
                        Text("デジタル").tag(0)
                        Text("アナログ").tag(1)
                        Text("両方").tag(2)
                    }
                }
                Section("アナログ時計") {
                    Toggle("文字盤に数字を表示", isOn: $showNumbers)
                    Toggle("分の目盛りを表示", isOn: $showMinuteTicks)
                    Toggle("秒針を表示", isOn: $showSecondHand)
                    Picker("数字の大きさ", selection: $numberScale) {
                        Text("小").tag(0)
                        Text("中").tag(1)
                        Text("大").tag(2)
                    }
                    .pickerStyle(.segmented)
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        IgnidoAnalogClockFace(date: context.date, timeZone: .current)
                            .frame(width: 240, height: 240)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(IgnidoTheme.background)
            .foregroundStyle(IgnidoTheme.text)
            .tint(IgnidoTheme.ember)
            .navigationTitle("時計の詳細設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完了") { dismiss() }
                }
            }
        }
    }
}

private struct IgnidoDial: View {
    let hourAngle: Double?
    let minuteAngle: Double?
    let secondAngle: Double?
    @AppStorage("ignido.clock.analog.numbers") private var showNumbers = true
    @AppStorage("ignido.clock.analog.ticks") private var showMinuteTicks = true
    @AppStorage("ignido.clock.analog.seconds") private var showSecondHand = true
    @AppStorage("ignido.clock.analog.numberScale") private var numberScale = 1

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width, geo.size.height)
            let radius = s * 0.43
            ZStack {
                Circle()
                    .fill(IgnidoTheme.surface)
                    .overlay(Circle().stroke(IgnidoTheme.border.opacity(0.9), lineWidth: max(1, s * 0.006)))

                if showMinuteTicks {
                    ForEach(0..<60, id: \.self) { i in
                        Capsule()
                            .fill(i % 5 == 0 ? IgnidoTheme.text : IgnidoTheme.secondaryText.opacity(0.75))
                            .frame(width: i % 5 == 0 ? max(2, s * 0.012) : max(1, s * 0.006),
                                   height: i % 5 == 0 ? s * 0.055 : s * 0.028)
                            .offset(y: -s * 0.445)
                            .rotationEffect(.degrees(Double(i) * 6))
                    }
                } else {
                    ForEach(0..<12, id: \.self) { i in
                        Capsule()
                            .fill(IgnidoTheme.text)
                            .frame(width: max(2, s * 0.012), height: s * 0.052)
                            .offset(y: -s * 0.44)
                            .rotationEffect(.degrees(Double(i) * 30))
                    }
                }

                if showNumbers {
                    ForEach(1...12, id: \.self) { n in
                        let a = Double(n) * 30 - 90
                        let rad = a * .pi / 180
                        let scale: CGFloat = numberScale == 0 ? 0.82 : (numberScale == 2 ? 1.18 : 1.0)
                        Text("\(n)")
                            .font(.system(size: max(9, s * 0.075 * scale), weight: n % 3 == 0 ? .semibold : .medium, design: .rounded))
                            .foregroundStyle(IgnidoTheme.text)
                            .position(x: s / 2 + cos(rad) * radius * 0.78,
                                      y: s / 2 + sin(rad) * radius * 0.78)
                    }
                }

                if let hourAngle {
                    hand(length: s * 0.23, width: max(3, s * 0.025), angle: hourAngle, color: IgnidoTheme.text)
                }
                if let minuteAngle {
                    hand(length: s * 0.32, width: max(2, s * 0.017), angle: minuteAngle, color: IgnidoTheme.text)
                }
                if showSecondHand, let secondAngle {
                    hand(length: s * 0.37, width: max(1.5, s * 0.009), angle: secondAngle, color: IgnidoTheme.ember)
                }
                Circle().fill(IgnidoTheme.ember).frame(width: s * 0.055, height: s * 0.055)
            }
            .frame(width: s, height: s)
        }
        .aspectRatio(1, contentMode: .fit)
    }

    private func hand(length: CGFloat, width: CGFloat, angle: Double, color: Color) -> some View {
        Capsule()
            .fill(color)
            .frame(width: width, height: length)
            .offset(y: -length / 2)
            .rotationEffect(.degrees(angle))
    }
}

struct IgnidoAnalogClockFace: View {
    let date: Date
    let timeZone: TimeZone
    var body: some View {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let c = calendar.dateComponents([.hour, .minute, .second], from: date)
        let sec = Double(c.second ?? 0)
        let min = Double(c.minute ?? 0) + sec / 60
        let hour = Double(c.hour ?? 0) + min / 60
        IgnidoDial(hourAngle: hour * 30, minuteAngle: min * 6, secondAngle: sec * 6)
    }
}

struct IgnidoStopwatchFace: View {
    let elapsed: TimeInterval
    var body: some View {
        let seconds = elapsed.truncatingRemainder(dividingBy: 60)
        let minutes = (elapsed / 60).truncatingRemainder(dividingBy: 60)
        let hours = elapsed / 3600
        IgnidoDial(hourAngle: hours * 30, minuteAngle: minutes * 6, secondAngle: seconds * 6)
    }
}

struct IgnidoProgressFace: View {
    let remaining: TimeInterval
    let total: TimeInterval
    var body: some View {
        let ratio = min(max(remaining / max(total, 1), 0), 1)
        ZStack {
            IgnidoDial(hourAngle: nil, minuteAngle: nil, secondAngle: (1 - ratio) * 360)
            Circle()
                .trim(from: 0, to: ratio)
                .stroke(IgnidoTheme.ember, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(8)
        }
    }
}
