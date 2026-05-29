package com.smarthome.guardian.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole

// ── Requests ──────────────────────────────────────────────────────────────────

data class LoginRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String,
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class BiometricRegisterRequest(
    @SerializedName("public_key")  val publicKey: String,  // Base64 EC public key
    @SerializedName("device_name") val deviceName: String,
)

data class VerifyPinRequest(
    @SerializedName("pin")    val pin: String,
    @SerializedName("email")  val email: String,
)

// ── Responses ─────────────────────────────────────────────────────────────────

data class LoginResponse(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("user")          val user: UserDto,
)

data class UserDto(
    @SerializedName("id")             val id: String,
    @SerializedName("email")          val email: String,
    @SerializedName("name")           val name: String,
    @SerializedName("role")           val role: String,
    @SerializedName("avatar_url")     val avatarUrl: String?,
    @SerializedName("last_login_at")  val lastLoginAt: Long,
) {
    fun toDomain(): User = User(
        id          = id,
        email       = email,
        name        = name,
        role        = runCatching { UserRole.valueOf(role.uppercase()) }.getOrDefault(UserRole.GUEST),
        avatarUrl   = avatarUrl,
        lastLoginAt = lastLoginAt,
    )
}
