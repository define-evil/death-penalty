package de.randombyte.deathpenalty

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = "Relative or fixed => with or without percent sign") val xpReduction: String = "50%",
        @Setting(comment = "Relative or fixed => with or without percent sign") val moneyReduction: String = "0%",
        @Setting(comment = "Potion effects applied at death. You can do 'copy & paste' to add new effects.")
            val potionEffects: List<PotionEffectConfig> = listOf(PotionEffectConfig()),
        @Setting(comment = "Don't modify this value! It is for internal use.") val recentlyDiedPlayers: List<UUID> = emptyList()
)

@ConfigSerializable
data class PotionEffectConfig(
        @Setting val id: String = "slowness",
        @Setting(comment = "Duration in seconds") val duration: Int = 180,
        @Setting val amplifier: Int = 1,
        @Setting val showParticles: Boolean = false
)