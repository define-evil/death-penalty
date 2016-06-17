package de.randombyte.deathpenalty
import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.data.manipulator.mutable.PotionEffectData
import org.spongepowered.api.effect.potion.PotionEffect
import org.spongepowered.api.effect.potion.PotionEffectTypes
import org.spongepowered.api.entity.EntityTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.cause.NamedCause
import org.spongepowered.api.event.entity.DestructEntityEvent
import org.spongepowered.api.event.entity.living.humanoid.player.RespawnPlayerEvent
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.event.service.ChangeServiceProviderEvent
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.economy.EconomyService
import java.math.BigDecimal
import java.util.*

@Plugin(id = DeathPenalty.ID, name = DeathPenalty.NAME, version = DeathPenalty.VERSION, authors = arrayOf(DeathPenalty.AUTHOR))
class DeathPenalty @Inject constructor(val logger: Logger, @DefaultConfig(sharedRoot = true) val configLoader: ConfigurationLoader<CommentedConfigurationNode>) {
    companion object {
        const val ID = "deathpenalty"
        const val NAME = "de.randombyte.deathpenalty.DeathPenalty"
        const val VERSION = "v0.1.2"
        const val AUTHOR = "RandomByte"
    }

    private var economyService: EconomyService? = null

    @Listener
    fun onInit(event: GameInitializationEvent) {
        logger.info("${NAME} loaded: ${VERSION}")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        logger.info("Reloaded config of ${NAME}!")
    }

    @Listener
    fun onNewEconomyService(event: ChangeServiceProviderEvent) {
        if (event.service.equals(EconomyService::class.java))
            economyService = event.newProviderRegistration.provider as EconomyService
    }

    @Listener
    fun onPlayerDeath(event: DestructEntityEvent.Death) {
        if (!event.targetEntity.type.equals(EntityTypes.PLAYER)) return
        val config = loadConfig()
        saveConfig(config.copy(recentlyDiedPlayers = config.recentlyDiedPlayers + event.targetEntity.uniqueId))
    }

    @Listener
    fun onPlayerRespawn(event: RespawnPlayerEvent) {
        val config = loadConfig()

        val shouldDoFinancialPunishment = lazy {
            if (config.moneyMultiplier < 0) false else {
                if (economyService == null) {
                    logger.warn("${NAME} can't perform financial punishment on just respawned player because there is no " +
                            "no economy plugin present!")
                    false
                } else true
            }
        }

        val player = event.targetEntity

        if (config.recentlyDiedPlayers.contains(player.uniqueId)) {
            if (shouldDoFinancialPunishment.value) doFinancialPunishment(player.uniqueId, config.moneyMultiplier)
            if (config.xpMultiplier >= 0) doXpPunishment(player, config.xpMultiplier)
            if (config.timeWithBlindness > 0) doBlindnessPunishement(player, config.timeWithBlindness * 20)
            saveConfig(config.copy(recentlyDiedPlayers = config.recentlyDiedPlayers - player.uniqueId))
        }
    }

    private fun doFinancialPunishment(player: UUID, multiplier: Double) {
        economyService!!.getOrCreateAccount(player).ifPresent { account ->
            val newBalance = account.getBalance(economyService!!.defaultCurrency) .multiply(BigDecimal(multiplier))
            account.setBalance(economyService!!.defaultCurrency, newBalance, Cause.of(NamedCause.source(this)))
        }
    }

    private fun doXpPunishment(player: Player, multiplier: Double) {
        player.get(Keys.TOTAL_EXPERIENCE).ifPresent { xps ->
            player.offer(Keys.TOTAL_EXPERIENCE, (xps * multiplier).toInt())
        }
    }

    private fun doBlindnessPunishement(player: Player, ticks: Int) {
        //https://github.com/SpongePowered/SpongeCommon/issues/794
        Sponge.getScheduler().createTaskBuilder().execute { ->
            player.getOrCreate(PotionEffectData::class.java).ifPresent {
                player.offer(it.addElement(PotionEffect.of(PotionEffectTypes.BLINDNESS, 1, ticks)))
            }
        }.delayTicks(8).submit(this)
    }

    private fun getRootNode() = configLoader.load()
    private fun loadConfig(): Config {
        val config = getRootNode().getValue(TypeToken.of(Config::class.java))
        return if (config != null) config else {
            saveConfig(Config()) //Set default de.randombyte.deathpenalty.Config when no config was found
            loadConfig()
        }
    }
    private fun saveConfig(config: Config) = configLoader.save(getRootNode().setValue(TypeToken.of(Config::class.java), config))
}