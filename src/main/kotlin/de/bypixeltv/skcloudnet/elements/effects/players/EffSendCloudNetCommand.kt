package de.bypixeltv.skcloudnet.elements.effects.players

import ch.njol.skript.Skript
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import de.bypixeltv.skcloudnet.Main
import eu.cloudnetservice.driver.inject.InjectionLayer
import eu.cloudnetservice.driver.provider.ClusterNodeProvider
import org.bukkit.event.Event
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level


class EffSendCloudNetCommand : Effect() {
    private val clusterNodeProvider: ClusterNodeProvider? = InjectionLayer.ext().instance(ClusterNodeProvider::class.java)

    companion object{
        init {
            Skript.registerEffect(EffSendCloudNetCommand::class.java, "send (cloud|cloudnet) [command] %strings%")
        }
        val commandIdCounter: AtomicInteger = AtomicInteger()
    }

    private var command: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.command = expressions[0] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "Send cloudnet command '${this.command}'"
    }

    override fun execute(e: Event?) {
        val cmd = command?.getSingle(e)
        if(cmd.isNullOrBlank()) {
            Skript.error("Command cannot be blank")
            return
        }
        val prefix = String.format("[SendCloudNetCommand #${commandIdCounter.incrementAndGet()}]")
        Main.INSTANCE.server.consoleSender.sendMessage("$prefix > $cmd")
        clusterNodeProvider?.sendCommandLineAsync(cmd)
            ?.thenAccept { result ->
                result.forEach { line ->
                    Main.INSTANCE.server.consoleSender.sendMessage("$prefix < $line")
                }
            }
            ?.exceptionally { err ->
                Main.INSTANCE.logger.log(Level.SEVERE, "$prefix Error during processing of command:", err)
                null
            }
    }
}
