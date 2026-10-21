// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "documents")
public class StoredDocument {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    @NonNull public String source = "";
    @NonNull public String inputFormat = "plain";
    @NonNull public String locale = "en-US";
    public long docVersion;
    @NonNull public String createdAt = "";
    @NonNull public String updatedAt = "";
    public int schemaVersion = 1;
    @NonNull public String provenanceJson = "[]";
}

