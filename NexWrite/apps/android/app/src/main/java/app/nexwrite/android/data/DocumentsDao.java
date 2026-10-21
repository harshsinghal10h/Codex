// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DocumentsDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC") List<StoredDocument> all();
    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1") StoredDocument find(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void save(StoredDocument document);
    @Query("DELETE FROM documents WHERE id = :id") void delete(String id);
    @Query("DELETE FROM documents") void deleteAll();
}

