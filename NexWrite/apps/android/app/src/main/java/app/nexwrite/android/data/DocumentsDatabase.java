// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StoredDocument.class}, version = 1, exportSchema = false)
public abstract class DocumentsDatabase extends RoomDatabase {
    public abstract DocumentsDao documents();
}

