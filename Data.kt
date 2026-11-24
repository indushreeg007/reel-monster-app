package com.reelmonster.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

enum class Source { INSTAGRAM, YOUTUBE }

/** One row per calendar day. History is never deleted at midnight: a new day simply gets a new row. */
@Entity(tableName = "day_stats")
data class DayStats(
    @PrimaryKey val date: String,
    val instagram: Int = 0,
    val youtube: Int = 0,
    val measuredSeconds: Int = 0
) { val total: Int get() = instagram + youtube }

@Dao
interface DayDao {
    @Query("SELECT * FROM day_stats WHERE date = :d") fun observe(d: String): Flow<DayStats?>
    @Query("SELECT * FROM day_stats WHERE date >= :from ORDER BY date") fun observeSince(from: String): Flow<List<DayStats>>
    @Query("SELECT * FROM day_stats WHERE date = :d") suspend fun get(d: String): DayStats?
    @Query("SELECT MAX(instagram + youtube) FROM day_stats") fun observeRecord(): Flow<Int?>
    @Upsert suspend fun upsert(s: DayStats)
    @Query("DELETE FROM day_stats") suspend fun clear()
}

@Database(entities = [DayStats::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): DayDao
    companion object {
        @Volatile private var I: AppDb? = null
        fun get(c: Context): AppDb = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "reel_monster.db").build().also { I = it }
        }
    }
}

class Repo private constructor(ctx: Context) {
    private val dao = AppDb.get(ctx).dao()
    private val mutex = Mutex()

    fun today(): String = LocalDate.now().toString()

    /** Emits the current date, re-checking every 20s, so the UI rolls over at midnight by itself. */
    private val dateTicker = flow { while (true) { emit(today()); delay(20_000) } }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayFlow: Flow<DayStats> = dateTicker.flatMapLatest { d -> dao.observe(d).map { it ?: DayStats(d) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekFlow: Flow<List<DayStats>> = dateTicker.flatMapLatest { d ->
        val t = LocalDate.parse(d)
        dao.observeSince(t.minusDays(6).toString()).map { rows ->
            (6 downTo 0).map { off ->
                val k = t.minusDays(off.toLong()).toString()
                rows.firstOrNull { it.date == k } ?: DayStats(k)
            }
        }
    }

    val record: Flow<Int> = dao.observeRecord().map { it ?: 0 }

    suspend fun addReel(src: Source): Int = mutex.withLock {
        val d = today(); val c = dao.get(d) ?: DayStats(d)
        val n = if (src == Source.INSTAGRAM) c.copy(instagram = c.instagram + 1) else c.copy(youtube = c.youtube + 1)
        dao.upsert(n); n.total
    }

    suspend fun addSeconds(s: Int) = mutex.withLock {
        val d = today(); val c = dao.get(d) ?: DayStats(d)
        dao.upsert(c.copy(measuredSeconds = c.measuredSeconds + s))
    }

    suspend fun adjust(src: Source, delta: Int) = mutex.withLock {
        val d = today(); val c = dao.get(d) ?: DayStats(d)
        dao.upsert(
            if (src == Source.INSTAGRAM) c.copy(instagram = (c.instagram + delta).coerceAtLeast(0))
            else c.copy(youtube = (c.youtube + delta).coerceAtLeast(0))
        )
    }

    suspend fun todayOnce(): DayStats = dao.get(today()) ?: DayStats(today())
    suspend fun clearAll() = mutex.withLock { dao.clear() }

    companion object {
        @Volatile private var I: Repo? = null
        fun get(c: Context): Repo = I ?: synchronized(this) { I ?: Repo(c.applicationContext).also { I = it } }
    }
}
