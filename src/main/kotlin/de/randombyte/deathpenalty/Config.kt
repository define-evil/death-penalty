package de.randombyte.deathpenalty

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = "Can be relative or fixed => with or without percent sign") val xpReduction: String = "50%",
        @Setting(comment = "Can be relative or fixed => with or without percent sign") val moneyReduction: String = "0%",
        @Setting(comment = "Time in seconds") val timeWithBlindness: Int = 180,
        @Setting(comment = "Don't modify this value!") val recentlyDiedPlayers: List<UUID> = emptyList()
)