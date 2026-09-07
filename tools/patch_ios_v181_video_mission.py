from pathlib import Path
import re

root = Path('ios/IGNIDOWake')

# Version 1.8.1.
plist = root / 'Info.plist'
s = plist.read_text(encoding='utf-8')
s = re.sub(r'(<key>CFBundleShortVersionString</key>\s*<string>)[^<]+(</string>)', r'\g<1>1.8.1\g<2>', s, count=1)
plist.write_text(s, encoding='utf-8')

project = Path('ios/project.yml')
s = project.read_text(encoding='utf-8')
s = re.sub(r'CFBundleShortVersionString: "[^"]+"', 'CFBundleShortVersionString: "1.8.1"', s)
project.write_text(s, encoding='utf-8')

# MissionView can now be rendered as a translucent overlay on top of a live video.
mission = root / 'MissionView.swift'
s = mission.read_text(encoding='utf-8')
old = '''struct MissionView: View {
    let alarm: WakeAlarm
    let onComplete: () -> Void
    @State private var effectiveMission: AlarmMission = .none
'''
new = '''struct MissionView: View {
    let alarm: WakeAlarm
    let onComplete: () -> Void
    var overVideo: Bool = false
    @State private var effectiveMission: AlarmMission = .none
'''
assert old in s, 'MissionView header anchor missing'
s = s.replace(old, new, 1)
old = '''        .background(IgnidoTheme.background.ignoresSafeArea())'''
new = '''        .background((overVideo ? Color.black.opacity(0.38) : IgnidoTheme.background).ignoresSafeArea())'''
assert old in s, 'MissionView background anchor missing'
s = s.replace(old, new, 1)
mission.write_text(s, encoding='utf-8')

# Keep the AVPlayer mounted while the mission is shown. v1.8.0 used a fullScreenCover,
# which put an opaque MissionView above the player and made the selected video disappear.
media = root / 'MediaPlayerView.swift'
s = media.read_text(encoding='utf-8')
old = '''            VStack(alignment: .trailing, spacing: 10) {
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
'''
new = '''            if showMission {
                MissionView(alarm: alarm, onComplete: { showMission = false; onDismiss() }, overVideo: true)
                    .transition(.opacity)
                    .zIndex(2)
            } else {
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
                        if alarm.mission == .none { onDismiss() } else { withAnimation(.easeOut(duration: 0.15)) { showMission = true } }
                    } label: {
                        Label(alarm.mission == .none ? "停止" : "解除", systemImage: "xmark.circle.fill")
                            .font(.headline).padding(.horizontal, 14).padding(.vertical, 10)
                            .background(.ultraThinMaterial, in: Capsule())
                    }
                }
                .padding()
            }
'''
assert old in s, 'MediaAlarmScreen controls anchor missing'
s = s.replace(old, new, 1)
old = '''        .fullScreenCover(isPresented: $showMission) {
            MissionView(alarm: alarm) { showMission = false; onDismiss() }
        }
'''
assert old in s, 'MediaAlarmScreen fullScreenCover anchor missing'
s = s.replace(old, '', 1)
media.write_text(s, encoding='utf-8')

# Update visible About version marker.
rv = root / 'RootView.swift'
s = rv.read_text(encoding='utf-8')
for oldver in ['iOS 1.7.4 beta','iOS 1.7.6 beta','iOS 1.7.7 beta','iOS 1.7.8 beta','iOS 1.8.0 beta']:
    s = s.replace(oldver, 'iOS 1.8.1 beta')
rv.write_text(s, encoding='utf-8')

# Validation.
assert '<string>1.8.1</string>' in plist.read_text(encoding='utf-8')
assert 'CFBundleShortVersionString: "1.8.1"' in project.read_text(encoding='utf-8')
ms = mission.read_text(encoding='utf-8')
assert 'var overVideo: Bool = false' in ms
assert 'Color.black.opacity(0.38)' in ms
ps = media.read_text(encoding='utf-8')
assert 'overVideo: true' in ps
assert '.fullScreenCover(isPresented: $showMission)' not in ps
assert 'WakeMediaPlayerView(url: url' in ps
print('IGNIDO Wake iOS 1.8.1 video mission overlay fix applied')
