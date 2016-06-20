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
import org.spongepowered.api.effect.potion.PotionEffectType
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
import org.spongepowered.api.world.gamerule.DefaultGameRules
import java.math.BigDecimal

@Plugin(id = DeathPenalty.ID, name = DeathPenalty.NAME, version = DeathPenalty.VERSION, authors = arrayOf(DeathPenalty.AUTHOR))
class DeathPenalty @Inject constructor(val logger: Logger, @DefaultConfig(sharedRoot = true) val configLoader: ConfigurationLoader<CommentedConfigurationNode>) {
    companion object {
        const val ID = "deathpenalty"
        const val NAME = "DeathPenalty"
        const val VERSION = "v0.3.0"
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
        val rootCause = event.cause.root()
        //Check if not the same player is the root cause(e.g. arrow shooting in the air to get damage)
        val isPlayerRootCause = rootCause is Player
        val isOtherPlayerRootCause = rootCause is Player && !rootCause.uniqueId.equals(event.targetEntity.uniqueId)
        if (isPlayerRootCause && !isOtherPlayerRootCause && !config.deathTypes.pvp) return
        saveConfig(config.copy(recentlyDiedPlayers = config.recentlyDiedPlayers + event.targetEntity.uniqueId))
    }

    @Listener
    fun onPlayerRespawn(event: RespawnPlayerEvent) {
        val config = loadConfig()
        fun String.valueAffected() = !equals(("0%")) && !equals("0")
        val player = event.targetEntity

        if (config.recentlyDiedPlayers.contains(player.uniqueId)) {

            val textTemplateParameters = mutableMapOf<String, Any?>()

            if (config.moneyReduction.valueAffected()) {
                //Check if financial punishment can be done(economy plugin present?)
                if (economyService == null) {
                    logger.warn("$NAME can't perform financial punishment on just respawned player because there is no " +
                            "no economy plugin present!")
                } else {
                    val lostMoney = doFinancialPunishment(player, config.moneyReduction)
                    textTemplateParameters["moneyLoss"] = lostMoney.toString()
                }
            }

            if (player.world.gameRules[DefaultGameRules.KEEP_INVENTORY]!!.toBoolean() && config.xpReduction.valueAffected()) {
                val lostXp = doXpPunishment(player, config.xpReduction)
                textTemplateParameters["xpLoss"] = lostXp
            }
            if (config.potionEffects.size > 0) {
                val appliedPotionEffects = doPotionEffectsPunishment(player, config.potionEffects)
                textTemplateParameters["potionEffects"] = appliedPotionEffects.joinToString(transform = { it.type.id.removePrefix("minecraft:") })
            }

            if (config.sendDeathMessage) player.sendMessage(config.deathMessage.apply(textTemplateParameters).build())
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
            logger.error("Config: Invalid '$nodeKey' config node!")
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
     * Must only be executed when [economyService] isn't null. See [getNewValueAfterReduction] for more information on
     * the formatting of the [reductionString].
     * @return The amount of lost money
     */
    private fun doFinancialPunishment(player: Player, reductionString: String): BigDecimal {
        val account = economyService!!.getOrCreateAccount(player.uniqueId).orElseThrow {
            RuntimeException("Failed to create an economy account for ${player.name}!")
        }
        val oldBalance = account.getBalance(economyService!!.defaultCurrency)
        val newBalance = getNewValueAfterReduction(oldBalance, "moneyReduction", reductionString)
        account.setBalance(economyService!!.defaultCurrency, newBalance, Cause.of(NamedCause.source(this)))
        return oldBalance.minus(newBalance)
    }

    /**
     * See [getNewValueAfterReduction] for more information on the formatting of the [reductionString].
     * @return The amount of lost XP's
     */
    private fun doXpPunishment(player: Player, reductionString: String): Int? {
        val optXps = player.get(Keys.TOTAL_EXPERIENCE)
        return if (optXps.isPresent) {
            val xps = optXps.get()
            val newExperience = getNewValueAfterReduction(BigDecimal(xps), "xpReduction", reductionString)
            player.offer(Keys.TOTAL_EXPERIENCE, newExperience.toInt())
            xps.minus(newExperience.toInt())
        } else null
    }

    private fun doPotionEffectsPunishment(player: Player, potionEffectConfigs: List<PotionEffectConfig>): List<PotionEffect> {
        val potionEffects = potionEffectConfigs.mapNotNull { potionEffectConfig ->
            val optEffect = Sponge.getRegistry().getType(PotionEffectType::class.java, potionEffectConfig.id)
            if (optEffect.isPresent) {
                return@mapNotNull PotionEffect.builder()
                        .potionType(optEffect.get())
                        .amplifier(potionEffectConfig.amplifier)
                        .duration(potionEffectConfig.duration * 20)
                        .particles(potionEffectConfig.showParticles)
                        .build()
            } else {
                logger.warn("Config: PotionEffect ID '${potionEffectConfig.id}' isn't registered!")
                return@mapNotNull null
            }
        }

        //https://github.com/SpongePowered/SpongeCommon/issues/794, waiting for new build
        Sponge.getScheduler().createTaskBuilder().execute { ->
            player.getOrCreate(PotionEffectData::class.java).ifPresent { potionEffectData ->
                potionEffects.forEach { potionEffectData.addElement(it) }
                player.offer(potionEffectData)
            }
        }.delayTicks(8).submit(this)

        return potionEffects
    }

    private fun getRootNode() = configLoader.load()
    private fun loadConfig(): Config {
        val config = getRootNode().getValue(TypeToken.of(Config::class.java))
        return if (config != null) config else {
            saveConfig(Config()) //Set default Config when no config was found
            loadConfig()
        }
    }
    private fun saveConfig(config: Config) = configLoader.save(getRootNode().setValue(TypeToken.of(Config::class.java), config))
}