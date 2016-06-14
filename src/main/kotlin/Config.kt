import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.util.*

@ConfigSerializable
data class Config(
    @Setting val xpMultiplier: Double = 0.5,
    @Setting val moneyMultiplier: Double = 1.0,
    @Setting(comment = "Time in seconds") val timeWithBlindness: Int = 180,
    @Setting val recentlyDiedPlayers: List<UUID> = emptyList()
)