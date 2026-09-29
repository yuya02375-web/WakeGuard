from pathlib import Path
root=Path('ios')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.4.0</string>','<string>2.4.2</string>',1).replace('<string>140</string>','<string>142</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.4.0"','CFBundleShortVersionString: "2.4.2"',1).replace('CFBundleVersion: "140"','CFBundleVersion: "142"',1)
write(p,s)

p='IGNIDOWake/StreakParity.swift'; s=read(p)
old='''                    VStack(spacing:20) {
                        HStack(spacing:18) {
                            GrowthCompanionView(level:store.growthLevel, streak:store.displayCurrent(alarms:alarmStore.alarms))
                                .frame(width:150,height:190)
                            VStack(alignment:.leading,spacing:5) {
                                Text("\\(store.displayCurrent(alarms:alarmStore.alarms))日")
                                    .font(.system(size:52,weight:.bold,design:.default)).monospacedDigit()
                                Text("現在のストリーク").font(.caption).foregroundStyle(.secondary)
                                Text("Lv.\\(store.growthLevel)").font(.headline).padding(.top,14)
                                Text(store.growthDescriptor).font(.caption).foregroundStyle(IgnidoTheme.ember)
                            }
                            Spacer(minLength:0)
                        }
                        Divider()
                        HStack { stat("最高",store.bestStreak);stat("成功",store.totalWakeups);stat("保護",store.balance) }
                        protectionCard
                        calendarCard
                        Button("今日の起床を記録") { store.recordWake(alarms:alarmStore.alarms) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                        NavigationLink("アプリ情報") { AboutView() }.foregroundStyle(IgnidoTheme.secondaryText)
                    }.padding(.horizontal,18).padding(.vertical,10)'''
new='''                    VStack(spacing:18) {
                        GrowthCompanionView(level:store.growthLevel, streak:store.displayCurrent(alarms:alarmStore.alarms))
                            .frame(maxWidth:.infinity)
                            .frame(height:320)
                        HStack(alignment:.bottom) {
                            VStack(alignment:.leading,spacing:3) {
                                Text("\\(store.displayCurrent(alarms:alarmStore.alarms))日")
                                    .font(.system(size:54,weight:.bold)).monospacedDigit()
                                Text("現在のストリーク").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("Lv.\\(store.growthLevel)")
                                .font(.title3.weight(.semibold))
                                .monospacedDigit()
                        }
                        Divider()
                        HStack { stat("最高",store.bestStreak);stat("成功",store.totalWakeups);stat("保護",store.balance) }
                        protectionCard
                        calendarCard
                        Button("今日の起床を記録") { store.recordWake(alarms:alarmStore.alarms) }.buttonStyle(.borderedProminent).tint(IgnidoTheme.ember)
                        NavigationLink("アプリ情報") { AboutView() }.foregroundStyle(IgnidoTheme.secondaryText)
                    }.padding(.horizontal,18).padding(.vertical,10)'''
if old not in s: raise SystemExit('iOS 2.4.0 streak block not found')
s=s.replace(old,new,1)
write(p,s)

assert '<string>2.4.2</string>' in read('IGNIDOWake/Info.plist')
assert '.frame(height:320)' in read('IGNIDOWake/StreakParity.swift')
assert '.frame(width:150,height:190)' not in read('IGNIDOWake/StreakParity.swift')
assert 'growthDescriptor' not in read('IGNIDOWake/StreakParity.swift').split('private var protectionCard')[0].split('struct StreakParityDashboard')[1]
assert 'TimeLogImportSheet(request:' in read('IGNIDOWake/ClockViews.swift')
print('iOS 2.4.2 big-flame UI applied')
