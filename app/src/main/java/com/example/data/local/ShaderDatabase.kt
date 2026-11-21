package com.example.data.local

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
import com.example.model.ShaderPreset
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shaders")
data class ShaderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val presetCategory: String,
    val targetPlatform: String,
    val summary: String,
    val fragmentShader: String,
    val sunWarmth: Double,
    val sunElevation: Double,
    val fogDensity: Double,
    val godRaysIntensity: Double,
    val waterReflectivity: Double,
    val giBounceIntensity: Double,
    val bloomIntensity: Double,
    val saturation: Double,
    val exposure: Double,
    val shadowSoftness: Double,
    val pbrMetallicBoost: Double,
    val skyZenithHex: String,
    val skyHorizonHex: String,
    val sunLightHex: String,
    val ambientTintHex: String,
    val waterTintHex: String,
    val fogTintHex: String,
    val favorite: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): ShaderPreset = ShaderPreset(
        id = id,
        title = title,
        prompt = prompt,
        presetCategory = presetCategory,
        targetPlatform = targetPlatform,
        summary = summary,
        fragmentShader = fragmentShader,
        sunWarmth = sunWarmth,
        sunElevation = sunElevation,
        fogDensity = fogDensity,
        godRaysIntensity = godRaysIntensity,
        waterReflectivity = waterReflectivity,
        giBounceIntensity = giBounceIntensity,
        bloomIntensity = bloomIntensity,
        saturation = saturation,
        exposure = exposure,
        shadowSoftness = shadowSoftness,
        pbrMetallicBoost = pbrMetallicBoost,
        skyZenithHex = skyZenithHex,
        skyHorizonHex = skyHorizonHex,
        sunLightHex = sunLightHex,
        ambientTintHex = ambientTintHex,
        waterTintHex = waterTintHex,
        fogTintHex = fogTintHex,
        favorite = favorite,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(preset: ShaderPreset, id: String, createdAt: Long, updatedAt: Long): ShaderEntity =
            ShaderEntity(
                id = id,
                title = preset.title,
                prompt = preset.prompt,
                presetCategory = preset.presetCategory,
                targetPlatform = preset.targetPlatform,
                summary = preset.summary,
                fragmentShader = preset.fragmentShader,
                sunWarmth = preset.sunWarmth,
                sunElevation = preset.sunElevation,
                fogDensity = preset.fogDensity,
                godRaysIntensity = preset.godRaysIntensity,
                waterReflectivity = preset.waterReflectivity,
                giBounceIntensity = preset.giBounceIntensity,
                bloomIntensity = preset.bloomIntensity,
                saturation = preset.saturation,
                exposure = preset.exposure,
                shadowSoftness = preset.shadowSoftness,
                pbrMetallicBoost = preset.pbrMetallicBoost,
                skyZenithHex = preset.skyZenithHex,
                skyHorizonHex = preset.skyHorizonHex,
                sunLightHex = preset.sunLightHex,
                ambientTintHex = preset.ambientTintHex,
                waterTintHex = preset.waterTintHex,
                fogTintHex = preset.fogTintHex,
                favorite = preset.favorite,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt
            )
    }
}

@Dao
interface ShaderDao {
    @Query("SELECT * FROM shaders ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ShaderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShaderEntity)

    @Query("UPDATE shaders SET favorite = :favorite, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM shaders WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [ShaderEntity::class], version = 1, exportSchema = false)
abstract class ShaderDatabase : RoomDatabase() {
    abstract fun shaderDao(): ShaderDao

    companion object {
        @Volatile
        private var instance: ShaderDatabase? = null

        fun getInstance(context: Context): ShaderDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShaderDatabase::class.java,
                    "lumina_shader_forge.db"
                ).build().also { instance = it }
            }
        }
    }
}
