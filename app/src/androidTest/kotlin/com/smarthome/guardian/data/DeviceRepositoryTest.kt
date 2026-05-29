package com.smarthome.guardian.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarthome.guardian.data.local.database.AppDatabase
import com.smarthome.guardian.data.local.database.DeviceDao
import com.smarthome.guardian.data.local.database.entity.DeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [DeviceDao] + Room in-memory 整合測試。
 *
 * 使用真實 Room 資料庫（不加密）驗證：
 * - CRUD 操作正確性
 * - Flow 在資料變更時自動 emit
 * - 按房間篩選
 * - Upsert 行為（insert + update）
 *
 * 以 [AndroidJUnit4] 執行（需要 Android 執行環境）。
 */
@RunWith(AndroidJUnit4::class)
class DeviceRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DeviceDao

    @Before
    fun setUp() {
        // in-memory 資料庫：測試結束後自動清除，不影響實際資料
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries() // 測試環境允許主執行緒查詢
            .build()

        dao = database.deviceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── 基本 CRUD ─────────────────────────────────────────────────────────────

    @Test
    fun upsert_single_device_and_read_back() = runTest {
        val device = buildDevice("d1", "智慧燈", "living_room")
        dao.upsert(device)

        val loaded = dao.getById("d1")
        assertNotNull(loaded)
        assertEquals("智慧燈", loaded?.name)
        assertEquals("living_room", loaded?.roomId)
    }

    @Test
    fun upsert_updates_existing_device() = runTest {
        val original = buildDevice("d1", "舊名稱", "room1")
        dao.upsert(original)

        val updated = original.copy(name = "新名稱", status = "OFFLINE")
        dao.upsert(updated)

        val loaded = dao.getById("d1")
        assertEquals("新名稱", loaded?.name)
        assertEquals("OFFLINE", loaded?.status)
    }

    @Test
    fun delete_removes_device_from_db() = runTest {
        val device = buildDevice("d_del", "待刪設備", "room1")
        dao.upsert(device)
        assertNotNull(dao.getById("d_del"))

        dao.delete(device)
        assertNull(dao.getById("d_del"))
    }

    @Test
    fun getById_returns_null_for_nonexistent() = runTest {
        val result = dao.getById("nonexistent-id")
        assertNull(result)
    }

    // ── 批次操作 ──────────────────────────────────────────────────────────────

    @Test
    fun upsertAll_inserts_multiple_devices() = runTest {
        val devices = listOf(
            buildDevice("d1", "燈光 A", "bedroom"),
            buildDevice("d2", "燈光 B", "bedroom"),
            buildDevice("d3", "門鎖",   "entrance"),
        )
        dao.upsertAll(devices)

        val all = dao.getAll().first()
        assertEquals(3, all.size)
    }

    // ── Flow 即時更新 ─────────────────────────────────────────────────────────

    @Test
    fun getAll_flow_emits_on_new_insert() = runTest {
        // 初始：空列表
        val emptyList = dao.getAll().first()
        assertEquals(0, emptyList.size)

        // 插入後 Flow 應 emit 新值
        dao.upsert(buildDevice("d1", "攝影機", "entrance"))
        val afterInsert = dao.getAll().first()
        assertEquals(1, afterInsert.size)
        assertEquals("d1", afterInsert.first().id)
    }

    @Test
    fun getByRoom_returns_only_matching_devices() = runTest {
        dao.upsertAll(listOf(
            buildDevice("d1", "客廳燈",   "living_room"),
            buildDevice("d2", "臥室燈",   "bedroom"),
            buildDevice("d3", "客廳攝影機","living_room"),
        ))

        val livingRoomDevices = dao.getByRoom("living_room").first()
        assertEquals(2, livingRoomDevices.size)
        assertTrue(livingRoomDevices.all { it.roomId == "living_room" })

        val bedroomDevices = dao.getByRoom("bedroom").first()
        assertEquals(1, bedroomDevices.size)
    }

    @Test
    fun deleteAll_clears_all_devices() = runTest {
        dao.upsertAll(listOf(
            buildDevice("d1", "燈 A", "r1"),
            buildDevice("d2", "燈 B", "r2"),
        ))
        assertEquals(2, dao.getAll().first().size)

        dao.deleteAll()
        assertEquals(0, dao.getAll().first().size)
    }

    // ── 狀態欄位 ─────────────────────────────────────────────────────────────

    @Test
    fun device_status_can_be_updated() = runTest {
        val device = buildDevice("d_status", "感應器", "entrance", status = "ONLINE")
        dao.upsert(device)

        dao.upsert(device.copy(status = "OFFLINE"))
        val updated = dao.getById("d_status")
        assertEquals("OFFLINE", updated?.status)
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun buildDevice(
        id: String,
        name: String,
        roomId: String,
        status: String = "ONLINE",
        type: String   = "LIGHT",
    ) = DeviceEntity(
        id            = id,
        name          = name,
        type          = type,
        roomId        = roomId,
        status        = status,
        isLocked      = false,
        lastSeen      = System.currentTimeMillis(),
        firmware      = "1.0.0",
        macAddress    = "AA:BB:CC:DD:EE:FF",
        isOn          = false,
        batteryLevel  = null,
        signalStrength= null,
    )

    private fun assertTrue(value: Boolean) =
        org.junit.Assert.assertTrue(value)
}
