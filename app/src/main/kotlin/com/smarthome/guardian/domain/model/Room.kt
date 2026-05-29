package com.smarthome.guardian.domain.model

/**
 * 家庭房間資料，用於儀表板的房間篩選 Chip。
 *
 * @property id          唯一識別碼（`"all"` 代表全部）
 * @property name        顯示名稱
 * @property deviceCount 此房間中的設備數量
 */
data class Room(
    val id: String,
    val name: String,
    val deviceCount: Int = 0,
) {
    companion object {
        /** 「全部」虛擬房間，不對應實際房間。 */
        val ALL = Room(id = "all", name = "全部")

        /** 預設房間清單（真實資料由後端提供）。 */
        val defaults = listOf(
            ALL,
            Room(id = "living_room", name = "客廳"),
            Room(id = "bedroom",     name = "臥室"),
            Room(id = "kitchen",     name = "廚房"),
            Room(id = "entrance",    name = "門口"),
        )
    }
}
