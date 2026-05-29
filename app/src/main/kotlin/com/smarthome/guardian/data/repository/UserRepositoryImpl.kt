package com.smarthome.guardian.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.smarthome.guardian.domain.model.QrCodeData
import com.smarthome.guardian.domain.model.User
import com.smarthome.guardian.domain.model.UserRole
import com.smarthome.guardian.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UserRepository] 的記憶體實作（PROMPT 09 完成後接入 REST API）。
 */
@Singleton
class UserRepositoryImpl @Inject constructor() : UserRepository {

    private val _users    = MutableStateFlow<List<User>>(emptyList())
    private val _qrCodes  = MutableStateFlow<List<QrCodeData>>(emptyList())

    override fun getUsers(): Flow<List<User>> = _users

    override fun getUser(userId: String): Flow<User?> =
        _users.map { it.find { u -> u.id == userId } }

    override suspend fun inviteUser(email: String, role: UserRole): Result<Unit> = runCatching {
        // TODO PROMPT 09: REST POST /api/v1/users { email, role }
        Timber.d("Invite user: $email role=$role")
    }

    override suspend fun updateUserRole(userId: String, role: UserRole): Result<Unit> = runCatching {
        _users.value = _users.value.map {
            if (it.id == userId) it.copy(role = role) else it
        }
        Timber.d("Role updated: userId=$userId role=$role")
    }

    override suspend fun revokeUserAccess(userId: String): Result<Unit> = runCatching {
        _users.value = _users.value.filter { it.id != userId }
        Timber.d("Access revoked: userId=$userId")
    }

    override suspend fun generateGuestQrCode(
        deviceIds: List<String>,
        expiresAt: Long,
        guestEmail: String?,
    ): Result<QrCodeData> = runCatching {
        val id      = UUID.randomUUID().toString()
        val payload = """{"id":"$id","devices":${deviceIds},"exp":$expiresAt}"""
        val bitmap  = generateQrBitmap(payload)
        val data    = QrCodeData(
            id               = id,
            guestLabel       = guestEmail ?: "訪客",
            allowedDeviceIds = deviceIds,
            expiresAt        = expiresAt,
            qrPayload        = payload,
            bitmap           = bitmap,
        )
        _qrCodes.value = _qrCodes.value + data
        Timber.d("QR code generated: id=${id.take(8)}…")
        data
    }

    override fun getQrCodes(): Flow<List<QrCodeData>> = _qrCodes

    override suspend fun revokeQrCode(code: String): Result<Unit> = runCatching {
        _qrCodes.value = _qrCodes.value.map {
            if (it.id == code) it.copy(isRevoked = true) else it
        }
        Timber.d("QR code revoked: id=${code.take(8)}…")
    }

    // ── ZXing QR Code 生成 ────────────────────────────────────────────────────

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits  = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp   = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
