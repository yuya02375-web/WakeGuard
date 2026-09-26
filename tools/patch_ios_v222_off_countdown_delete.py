from pathlib import Path

root=Path('ios')
def read(p): return (root/p).read_text()
def write(p,s): (root/p).write_text(s)

p='IGNIDOWake/Info.plist'; s=read(p)
s=s.replace('<string>2.2.1</string>','<string>2.2.2</string>',1).replace('<string>121</string>','<string>122</string>',1)
write(p,s)
p='project.yml'; s=read(p)
s=s.replace('CFBundleShortVersionString: "2.2.1"','CFBundleShortVersionString: "2.2.2"',1).replace('CFBundleVersion: "121"','CFBundleVersion: "122"',1)
write(p,s)

p='IGNIDOWake/AlarmViews.swift'; s=read(p)
old='''                    if alarm.enabled {
                        TimelineView(.periodic(from: .now, by: 30)) { context in
                            if let remaining = alarm.remainingText(at: context.date) {
                                Text(remaining).foregroundStyle(IgnidoTheme.ember)
                            }
                        }
                    }'''
new='''                    TimelineView(.periodic(from: .now, by: 30)) { context in
                        if let remaining = alarm.remainingText(at: context.date) {
                            Text(remaining)
                                .foregroundStyle(alarm.enabled ? IgnidoTheme.ember : IgnidoTheme.secondaryText)
                        }
                    }'''
if old not in s: raise SystemExit('iOS alarm remaining visibility block not found')
s=s.replace(old,new,1)
write(p,s)

p='IGNIDOWake/TimeLogView.swift'; s=read(p)
s=s.replace('''    @State private var editEntry: EntryEditorState?
    @State private var rename = false
    @State private var deleteFolderConfirm = false
    @State private var deleteEntryConfirm: TimeLogEntry?
''','''    @State private var deleteFolderConfirm = false
    @State private var deleteEntryConfirm: TimeLogEntry?
    @State private var editEntry: EntryEditorState?
''',1)
old='''                            ForEach(rows) { e in EntryRow(entry: e, day: day).onTapGesture { editEntry = EntryEditorState(entry: e) }.contextMenu { Button("編集") { editEntry = EntryEditorState(entry: e) }; Button("削除", role: .destructive) { store.deleteEntry(e.id) } } }'''
new='''                            ForEach(rows) { e in
                                HStack(spacing: 8) {
                                    EntryRow(entry: e, day: day)
                                        .onTapGesture { editEntry = EntryEditorState(entry: e) }
                                    Button(role: .destructive) { deleteEntryConfirm = e } label: {
                                        Image(systemName: "trash")
                                            .frame(width: 36, height: 36)
                                    }
                                    .buttonStyle(.bordered)
                                    .accessibilityLabel("時間記録を削除")
                                }
                            }'''
if old not in s: raise SystemExit('iOS time-log row block not found')
s=s.replace(old,new,1)
old='''.confirmationDialog("フォルダーを削除しますか？", isPresented: $deleteFolderConfirm, titleVisibility: .visible) { Button("削除", role: .destructive) { store.deleteFolder(folderID); dismiss() } }'''
new='''.confirmationDialog("フォルダーを削除しますか？", isPresented: $deleteFolderConfirm, titleVisibility: .visible) { Button("削除", role: .destructive) { store.deleteFolder(folderID); dismiss() } }
        .confirmationDialog("この時間記録を削除しますか？", item: $deleteEntryConfirm, titleVisibility: .visible) { entry in
            Button("削除", role: .destructive) { store.deleteEntry(entry.id) }
            Button("キャンセル", role: .cancel) { }
        }'''
if old not in s: raise SystemExit('iOS confirmation insertion point not found')
s=s.replace(old,new,1)
write(p,s)

assert '<string>2.2.2</string>' in read('IGNIDOWake/Info.plist') and '<string>122</string>' in read('IGNIDOWake/Info.plist')
assert 'if alarm.enabled {' not in read('IGNIDOWake/AlarmViews.swift').split('if let remaining = alarm.remainingText')[0][-250:]
assert 'deleteEntryConfirm' in read('IGNIDOWake/TimeLogView.swift')
assert 'Image(systemName: "trash")' in read('IGNIDOWake/TimeLogView.swift')
print('iOS 2.2.2 OFF-countdown + visible time-record delete applied')
