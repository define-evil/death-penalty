package de.randombyte.deathpenalty

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.util.*

@ConfigSerializable
data class Config(
        @Setting val xpReduction: String = "50%",
        @Setting val moneyReduction: String = "0%",
        @Setting(comment = "Time in seconds") val timeWithBlindness: Int = 180,
        @Setting val recentlyDiedPlayers: List<UUID> = emptyList()
)