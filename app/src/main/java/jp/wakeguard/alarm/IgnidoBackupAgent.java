package jp.wakeguard.alarm;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

/** Sanitized key/value cloud backup used for reinstall/device restore. */
public final class IgnidoBackupAgent extends BackupAgent {
    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        try {
            byte[] bytes = BackupVault.snapshotBytes(this);
            data.writeEntityHeader(BackupVault.ENTITY_KEY, bytes.length);
            data.writeEntityData(bytes, bytes.length);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        while (data.readNextHeader()) {
            int size = data.getDataSize();
            byte[] bytes = new byte[Math.max(0, size)];
            int offset = 0;
            while (offset < bytes.length) {
                int n = data.readEntityData(bytes, offset, bytes.length - offset);
                if (n <= 0) break;
                offset += n;
            }
            if (BackupVault.ENTITY_KEY.equals(data.getKey()) && offset == bytes.length) {
                try { BackupVault.restoreBytes(this, bytes); }
                catch (Exception e) { throw new IOException(e); }
            }
        }
    }

    @Override public void onRestoreFinished() {
        super.onRestoreFinished();
        BackupVault.afterRestore(this);
    }
}
