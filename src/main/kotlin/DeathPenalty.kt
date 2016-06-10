
import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.loader.ConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Plugin

@Plugin(id = DeathPenalty.ID, name = DeathPenalty.NAME, version = DeathPenalty.VERSION, authors = arrayOf(DeathPenalty.AUTHOR))
class DeathPenalty @Inject constructor(val logger: Logger, @DefaultConfig(sharedRoot = true) val configLoader: ConfigurationLoader<CommentedConfigurationNode>) {
    companion object {
        const val ID = "deathpenalty"
        const val NAME = "DeathPenalty"
        const val VERSION = "v0.1"
        const val AUTHOR = "RandomByte"
    }

    private lateinit var config: Config

    @Listener
    fun onInit(event: GameInitializationEvent) {
        config = loadConfig()
        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        config = loadConfig()
    }

    private fun getRootNode() = configLoader.load()
    private fun loadConfig(): Config {
        val config = getRootNode().getValue(TypeToken.of(Config::class.java))
        return if (config != null) config else {
            saveConfig(Config())
            loadConfig()
        }
    }
    private fun saveConfig(config: Config) = configLoader.save(getRootNode().setValue(TypeToken.of(Config::class.java), config))
}