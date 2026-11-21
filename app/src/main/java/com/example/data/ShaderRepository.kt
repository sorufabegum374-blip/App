package com.example.data

import com.example.data.local.ShaderDao
import com.example.data.local.ShaderEntity
import com.example.model.ShaderPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ShaderRepository(
    private val shaderDao: ShaderDao
) {
    fun observeShaders(): Flow<List<ShaderPreset>> =
        shaderDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun saveShader(preset: ShaderPreset): Result<String> {
        return try {
            val clean = preset.sanitized()
            val now = System.currentTimeMillis()
            val id = clean.id.ifBlank { UUID.randomUUID().toString() }
            val createdAt = if (clean.createdAtMillis > 0L) clean.createdAtMillis else now
            shaderDao.upsert(ShaderEntity.fromDomain(clean, id, createdAt, now))
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(shaderId: String, nextFavorite: Boolean): Result<Unit> {
        return try {
            require(shaderId.isNotBlank()) { "Shader ID must not be blank." }
            shaderDao.updateFavorite(shaderId, nextFavorite, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteShader(shaderId: String): Result<Unit> {
        return try {
            require(shaderId.isNotBlank()) { "Shader ID must not be blank." }
            shaderDao.deleteById(shaderId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
