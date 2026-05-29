package com.smarthome.guardian.domain.model

/**
 * 設備可執行的操作類型。
 *
 * 用於 [com.smarthome.guardian.domain.repository.DeviceRepository.sendCommand]
 * 及存取控制規則 [AccessRule.allowedOperations] 的權限判斷。
 *
 * 與 [CommandType] 的差異：
 * - [DeviceOperation] 為 Domain 層的操作抽象（Repository 介面使用）
 * - [CommandType] 為完整的設備指令枚舉（含細粒度設定參數）
 */
enum class DeviceOperation {
    // 通用（向後相容）
    READ,
    TOGGLE,

    // 通用開關
    TOGGLE_ON,
    TOGGLE_OFF,

    // 門鎖
    LOCK,
    UNLOCK,
    GENERATE_TEMP_CODE,

    // 燈光
    SET_BRIGHTNESS,
    SET_COLOR_TEMP,
    SET_COLOR,
    APPLY_SCENE,
    SET_SCHEDULE,

    // 攝影機
    SET_SENSITIVITY,
    CAPTURE_SCREENSHOT,
    START_RECORDING,
    STOP_RECORDING,

    // 感應器
    SET_THRESHOLD,
    RESET_ALARM,

    // 系統
    REBOOT,
    UPDATE_FIRMWARE,
}
