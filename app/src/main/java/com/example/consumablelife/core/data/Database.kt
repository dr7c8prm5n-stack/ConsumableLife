package com.example.consumablelife.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.consumablelife.core.domain.CarZone
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.RecordAction
import com.example.consumablelife.core.domain.RecordSource
import com.example.consumablelife.core.domain.ReplacementRecord
import com.example.consumablelife.core.domain.SensorLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Singleton

/* 8장 데이터 모델 — DB 스키마 v2 (명세 v2.0 10장: cycleKind·isUserDefault·severeKind·action 추가) */

@Entity
data class ConsumableItemEntity(
    @PrimaryKey val id: String,              // "BATTERY_12V", "CUSTOM_1" ...
    val nameRes: String?, val customName: String?,
    val iconKey: String,
    val normalIntervalKm: Int?, val normalIntervalMonths: Int?,
    val severeIntervalKm: Int?, val severeIntervalMonths: Int?,
    val firstIntervalKm: Int?, val firstIntervalMonths: Int?,   // 냉각수 "최초" 주기
    val enabled: Boolean, val sortOrder: Int,
    val powertrains: Set<Powertrain>,
    val sensorLink: SensorLink?,
    val zone: CarZone,
    val needsConfirm: Boolean,
    val cycleKind: CycleKind,
    val isUserDefault: Boolean,
    val severeKind: CycleKind?,
)

@Entity(indices = [Index("itemId")])
data class ReplacementRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val odometerKm: Double,
    val dateEpochDay: Long,
    val source: RecordSource,
    val memo: String?,
    val createdAt: Long,
    val action: RecordAction = RecordAction.REPLACED,
)

@Entity
data class OdometerSnapshotEntity(
    @PrimaryKey val epochDay: Long, // 하루 1행 (그날 최댓값 upsert)
    val maxKm: Double,
)

class Converters {
    @TypeConverter fun powertrainsToString(v: Set<Powertrain>): String = v.joinToString(",") { it.name }
    @TypeConverter fun stringToPowertrains(v: String): Set<Powertrain> =
        v.split(',').filter { it.isNotBlank() }.map { Powertrain.valueOf(it) }.toSet()
}

@Dao
interface ConsumableDao {
    @Query("SELECT * FROM ConsumableItemEntity ORDER BY sortOrder")
    fun observeItems(): Flow<List<ConsumableItemEntity>>

    @Query("SELECT * FROM ConsumableItemEntity ORDER BY sortOrder")
    suspend fun items(): List<ConsumableItemEntity>

    @Query("SELECT COUNT(*) FROM ConsumableItemEntity")
    suspend fun itemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ConsumableItemEntity>)

    @Update suspend fun updateItem(item: ConsumableItemEntity)

    @Query("SELECT * FROM ConsumableItemEntity WHERE id = :id")
    suspend fun item(id: String): ConsumableItemEntity?

    @Query("DELETE FROM ConsumableItemEntity WHERE id = :id")
    suspend fun deleteItem(id: String)

    /** 프로필 교체: 사용자 항목(CUSTOM_*)이 아닌 프리셋 항목만 삭제 */
    @Query("DELETE FROM ConsumableItemEntity WHERE id NOT LIKE 'CUSTOM\\_%' ESCAPE '\\'")
    suspend fun deletePresetItems()

    @Query("SELECT * FROM ReplacementRecordEntity ORDER BY dateEpochDay DESC, odometerKm DESC, id DESC")
    fun observeRecords(): Flow<List<ReplacementRecordEntity>>

    @Insert suspend fun insertRecord(r: ReplacementRecordEntity): Long
    @Update suspend fun updateRecord(r: ReplacementRecordEntity)

    @Query("DELETE FROM ReplacementRecordEntity WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM ReplacementRecordEntity WHERE itemId = :itemId")
    suspend fun deleteRecordsFor(itemId: String)

    @Query("SELECT * FROM OdometerSnapshotEntity WHERE epochDay >= :fromEpochDay ORDER BY epochDay")
    fun observeSnapshots(fromEpochDay: Long): Flow<List<OdometerSnapshotEntity>>

    @Query("SELECT * FROM OdometerSnapshotEntity WHERE epochDay = :epochDay")
    suspend fun snapshot(epochDay: Long): OdometerSnapshotEntity?

    @Upsert suspend fun upsertSnapshot(s: OdometerSnapshotEntity)

    @Query("DELETE FROM ConsumableItemEntity") suspend fun clearItems()
    @Query("DELETE FROM ReplacementRecordEntity") suspend fun clearRecords()
    @Query("DELETE FROM OdometerSnapshotEntity") suspend fun clearSnapshots()
}

@Database(
    entities = [ConsumableItemEntity::class, ReplacementRecordEntity::class, OdometerSnapshotEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ConsumableDao
}

/** v1 → v2: 주기 종류·사용자 기본값·가혹 시 종류·기록 성격 컬럼 추가 (기존 행은 교체형/교체 기록으로 간주) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ConsumableItemEntity ADD COLUMN cycleKind TEXT NOT NULL DEFAULT 'REPLACE'")
        db.execSQL("ALTER TABLE ConsumableItemEntity ADD COLUMN isUserDefault INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ConsumableItemEntity ADD COLUMN severeKind TEXT")
        db.execSQL("ALTER TABLE ReplacementRecordEntity ADD COLUMN action TEXT NOT NULL DEFAULT 'REPLACED'")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "consumable_life.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun dao(db: AppDatabase): ConsumableDao = db.dao()
}

/* ---- Entity ↔ Domain ---- */

fun ConsumableItemEntity.toDomain() = ConsumableItem(
    id = id, customName = customName, iconKey = iconKey,
    normal = Interval(normalIntervalKm, normalIntervalMonths),
    severe = Interval(severeIntervalKm, severeIntervalMonths),
    first = if (firstIntervalKm == null && firstIntervalMonths == null) null else Interval(firstIntervalKm, firstIntervalMonths),
    enabled = enabled, sortOrder = sortOrder, powertrains = powertrains,
    sensorLink = sensorLink, zone = zone, needsConfirm = needsConfirm,
    cycleKind = cycleKind, isUserDefault = isUserDefault, severeKind = severeKind,
)

fun ConsumableItem.toEntity() = ConsumableItemEntity(
    id = id, nameRes = if (isCustom) null else id, customName = customName, iconKey = iconKey,
    normalIntervalKm = normal.km, normalIntervalMonths = normal.months,
    severeIntervalKm = severe.km, severeIntervalMonths = severe.months,
    firstIntervalKm = first?.km, firstIntervalMonths = first?.months,
    enabled = enabled, sortOrder = sortOrder, powertrains = powertrains,
    sensorLink = sensorLink, zone = zone, needsConfirm = needsConfirm,
    cycleKind = cycleKind, isUserDefault = isUserDefault, severeKind = severeKind,
)

fun ReplacementRecordEntity.toDomain() = ReplacementRecord(
    id = id, itemId = itemId, odometerKm = odometerKm,
    date = LocalDate.ofEpochDay(dateEpochDay), source = source, memo = memo, action = action,
)
