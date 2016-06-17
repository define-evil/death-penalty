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
        const val NAME = "DeathPenalty"
        const val VERSION = "v0.1.2"
        const val AUTHOR = "RandomByte"
    }

    private var economyService: EconomyService? = null

    @Listener
    fun onInit(event: GameInitializationEvent) {
        loadConfig()
        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        loadConfig()
        logger.info("Reloaded config of $NAME!")
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
        fun String.valueUnaffected() = equals(("0%")) || equals("0")
        val player = event.targetEntity

        if (config.recentlyDiedPlayers.contains(player.uniqueId)) {

            //Check if financial punishment can be done(economy plugin present?)
            if (!config.moneyReduction.valueUnaffected()) {
                if (economyService == null) {
                    logger.warn("$NAME can't perform financial punishment on just respawned player because there is no " +
                            "no economy plugin present!")
                } else {
                    //Now we can do it
                    doFinancialPunishment(player.uniqueId, config.moneyReduction)
                }
            }

            if (!config.xpReduction.valueUnaffected()) doXpPunishment(player, config.xpReduction)
            if (config.timeWithBlindness > 0) doBlindnessPunishment(player, config.timeWithBlindness * 20)
            saveConfig(config.copy(recentlyDiedPlayers = config.recentlyDiedPlayers - player.uniqueId))
        }
    }

    /**
     * Calculates a new [BigDecimal] based on the [oldValue] which is manipulated by the [nodeValue]. It can be a fixed
     * value or a relative value with a percent sign: e.g. "25", "40%". The [nodeKey] is for logging purposes on failure.
     * [BigDecimal]s are used in this method to cover the use case of calculating the player's balance after he died.
     */
    private fun getNewValueAfterReduction(oldValue: BigDecimal, nodeKey: String, nodeValue: String): BigDecimal {
        fun String.isNumber() = this.toCharArray().all { it.isDigit() }
        fun String.tryToNumber(func: (Int) -> BigDecimal): BigDecimal = if (this.isNumber()) {
            func.invoke(this.toInt())
        } else {
            logger.error("Invalid $nodeKey config!")
            oldValue
        }

        val reductionString = nodeValue
        return if (reductionString.contains("%")) reductionString.split("%")[0].tryToNumber {
            oldValue.multiply(BigDecimal.valueOf(1 - (it / 100.0)))
        } else reductionString.tryToNumber {
            oldValue.subtract(BigDecimal(reductionString))
        }
    }

    /**
     * See [getNewValueAfterReduction] for more information on the formatting of the [reductionString].
     */
    private fun doFinancialPunishment(player: UUID, reductionString: String) {
        economyService!!.getOrCreateAccount(player).ifPresent { account ->
            val oldBalance = account.getBalance(economyService!!.defaultCurrency)
            val newBalance = getNewValueAfterReduction(oldBalance, "moneyReduction", reductionString)
            account.setBalance(economyService!!.defaultCurrency, newBalance, Cause.of(NamedCause.source(this)))
        }
    }

    /**
     * See [getNewValueAfterReduction] for more information on the formatting of the [reductionString].
     */
    private fun doXpPunishment(player: Player, reductionString: String) {
        player.get(Keys.TOTAL_EXPERIENCE).ifPresent { xps ->
            val newExperience = getNewValueAfterReduction(BigDecimal(xps), "xpReduction", reductionString)
            player.offer(Keys.TOTAL_EXPERIENCE, newExperience.toInt())
        }
    }

    private fun doBlindnessPunishment(player: Player, ticks: Int) {
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