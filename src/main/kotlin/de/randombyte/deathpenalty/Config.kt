package de.randombyte.deathpenalty

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate
import org.spongepowered.api.text.format.TextColors
import java.util.*

@ConfigSerializable
data class Config(
        @Setting(comment = "Relative or fixed => with or without percent sign") val xpReduction: String = "50%",
        @Setting(comment = "Relative or fixed => with or without percent sign") val moneyReduction: String = "0%",
        @Setting(comment = "Potion effects applied at death. You can do 'copy & paste' to add new effects.")
            val potionEffects: List<PotionEffectConfig> = listOf(PotionEffectConfig()),
        @Setting val sendDeathMessage: Boolean = true,
        @Setting(comment = "The message that gets sent to the died player.") val deathMessage: TextTemplate = TextTemplate.of(
                Text.of(TextColors.AQUA, "[DeathPenalty]"), " You lost ",
                TextTemplate.arg("moneyLoss").optional(true).defaultValue(Text.of("[Not provided]")),
                "$, ",
                TextTemplate.arg("xpLoss").optional(true).defaultValue(Text.of("[Not provided]")),
                " XP's and got the potion effect(s) ",
                TextTemplate.arg("potionEffects").optional(true).defaultValue(Text.of("[Not provided]"))
        ),
        @Setting(comment = "Ways to die when this plugin should NOT take any action.") val deathTypes: DeathTypes = DeathTypes(),
        @Setting(comment = "Don't modify this value! It is for internal use.") val recentlyDiedPlayers: List<UUID> = emptyList()
)

@ConfigSerializable
data class PotionEffectConfig(
        @Setting val id: String = "slowness",
        @Setting(comment = "Duration in seconds") val duration: Int = 180,
        @Setting val amplifier: Int = 1,
        @Setting val showParticles: Boolean = false
)

@ConfigSerializable
data class DeathTypes(
        @Setting val pvp: Boolean = true
)