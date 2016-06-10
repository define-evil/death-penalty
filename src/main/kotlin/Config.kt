import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class Config {
    @Setting val xpMultiplier: Double = 0.5
    @Setting val moneyMultiplier: Double = 0.7
    @Setting(comment = "Time in seconds") val timeWithBlindness: Int = 600
}