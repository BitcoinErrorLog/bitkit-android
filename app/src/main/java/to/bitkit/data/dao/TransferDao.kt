package to.bitkit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import to.bitkit.data.entities.TransferEntity

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("SELECT * FROM transfers WHERE isSettled = 0")
    fun getActiveTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE channelId = :channelId AND isSettled = 0 LIMIT 1")
    suspend fun getActiveByChannelId(channelId: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE lspOrderId = :orderId LIMIT 1")
    suspend fun getByLspOrderId(orderId: String): TransferEntity?

    @Query("UPDATE transfers SET channelId = :channelId, fundingTxId = :fundingTxId WHERE id = :id")
    suspend fun updateChannelInfo(id: String, channelId: String, fundingTxId: String?)

    @Query("UPDATE transfers SET isSettled = 1, settledAt = :settledAt WHERE id = :id")
    suspend fun markSettled(id: String, settledAt: Long)

    @Query("DELETE FROM transfers WHERE isSettled = 1 AND settledAt < :expirationTimestamp")
    suspend fun deleteOldSettled(expirationTimestamp: Long)

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    suspend fun getAll(): List<TransferEntity>
}
