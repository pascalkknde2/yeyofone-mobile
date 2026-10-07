package com.yeyofone.core.account

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yeyofone.core.model.Contact
import com.yeyofone.core.model.ContactId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomContactRepository private constructor(
    private val dao: ContactDao,
) : ContactRepository {
    override fun observeAll(): Flow<List<Contact>> = dao.observeAll().map { it.map(ContactEntity::toModel) }

    override suspend fun save(draft: ContactDraft): ContactId {
        ContactValidator.validate(draft)
        val id = draft.id ?: ContactId(UUID.randomUUID().toString())
        dao.upsert(
            ContactEntity(
                id = id.value,
                displayName = draft.displayName.trim(),
                number = draft.number.trim(),
                favorite = draft.favorite,
            ),
        )
        return id
    }

    override suspend fun setFavorite(id: ContactId, favorite: Boolean) {
        dao.setFavorite(id.value, favorite)
    }

    override suspend fun delete(id: ContactId) = dao.delete(id.value)

    companion object {
        fun create(context: Context): RoomContactRepository =
            RoomContactRepository(ContactDatabase.open(context.applicationContext).contacts())
    }
}

@Entity(tableName = "contacts")
internal data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val number: String,
    val favorite: Boolean,
)

@Dao
internal interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ContactEntity::class], version = 1, exportSchema = true)
internal abstract class ContactDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao

    companion object {
        @Volatile private var instance: ContactDatabase? = null

        fun open(context: Context): ContactDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ContactDatabase::class.java, "yeyofone-contacts.db")
                .build().also { instance = it }
        }
    }
}

private fun ContactEntity.toModel() = Contact(
    id = ContactId(id),
    displayName = displayName,
    number = number,
    favorite = favorite,
)
