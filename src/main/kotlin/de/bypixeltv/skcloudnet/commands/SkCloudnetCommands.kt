package de.bypixeltv.skcloudnet.commands

import ch.njol.skript.Skript
import ch.njol.skript.util.Version
import com.google.gson.Gson
import com.google.gson.JsonObject
import de.bypixeltv.skcloudnet.Main
import de.bypixeltv.skcloudnet.utils.UpdateChecker
import de.bypixeltv.skcloudnet.utils.UpdateChecker.Companion.getLatestReleaseVersion
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.*
import eu.cloudnetservice.driver.inject.InjectionLayer
import eu.cloudnetservice.driver.registry.ServiceRegistry
import eu.cloudnetservice.modules.bridge.node.player.NodePlayerManager
import eu.cloudnetservice.modules.bridge.player.CloudPlayer
import eu.cloudnetservice.modules.bridge.player.PlayerManager
import eu.cloudnetservice.modules.bridge.player.executor.PlayerExecutor
import net.axay.kspigot.chat.literalText
import net.axay.kspigot.extensions.onlinePlayers
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.jvm.optionals.getOrNull

class SkCloudnetCommands {
    private val miniMessages = MiniMessage.miniMessage()

    private val serviceRegistry: ServiceRegistry? = InjectionLayer.ext().instance(ServiceRegistry::class.java)
    private val playerManager: PlayerManager? = serviceRegistry?.firstProvider(PlayerManager::class.java)

    @Suppress("UNUSED", "DEPRECATION")
    val command = commandTree("skcloudnet") {
        withPermission("skcloudnet.admin")
        literalArgument("info") {
            withPermission("skcloudnet.admin.info")
            anyExecutor { player, _ ->
                val addonMessages = Skript.getAddons().mapNotNull { addon ->
                    val name = addon.name
                    if (!name.contains("SkCloudnet")) {
                        "<grey>-</grey> <aqua>$name</aqua> <yellow>v${addon.plugin.description.version}</yellow>"
                    } else {
                        null
                    }
                }

                val addonsList =
                    if (addonMessages.isNotEmpty()) addonMessages.joinToString("\n") else "<color:#ff0000>No other addons found</color>"
                player.sendMessage(
                    miniMessages.deserialize(
                        "<dark_grey>--- <aqua>SkCloudnet</aqua> <grey>Info:</grey> ---</dark_grey>\n\n<grey>SkCloudnet Version: <aqua>${Main.INSTANCE.description.version}</aqua>\nSkript Version: <aqua>${Skript.getInstance().description.version}</aqua>\nServer Version: <aqua>${Main.INSTANCE.server.minecraftVersion}</aqua>\nServer Implementation: <aqua>${Main.INSTANCE.server.version}</aqua>\nAddons:\n$addonsList</grey>"
                    )
                )
            }
        }
        literalArgument("docs") {
            withPermission("skcloudnet.admin.docs")
            anyExecutor { player, _ ->
                player.sendMessage(
                    miniMessages.deserialize(
                        "<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> <grey><aqua>Documentation</aqua> for <aqua>SkCloudnet:</aqua></grey>\n<grey>-</grey> <click:open_url:'https://skripthub.net/docs/?addon=SkCloudnet'><aqua>SkriptHub</aqua> <dark_grey>(<aqua>Click me!</aqua>)</dark_grey></click>\n<grey>-</grey> <click:open_url:'https://docs.skunity.com/syntax/search/addon:skcloudnet'><aqua>SkUnity</aqua> <dark_grey>(<aqua>Click me!</aqua>)</dark_grey></click>"
                    )
                )
            }
        }
        literalArgument("version") {
            withPermission("skcloudnet.admin.version")
            anyExecutor { player, _ ->
                val currentVersion = Main.INSTANCE.description.version
                val updateVersion = UpdateChecker(Main.INSTANCE).getUpdateVersion(currentVersion)

                getLatestReleaseVersion { version ->
                    val plugVer = Version(Main.INSTANCE.description.version)
                    val curVer = Version(version)
                    val url = URL("https://api.github.com/repos/byPixelTV/SkCloudnet/releases/latest")
                    val reader = BufferedReader(InputStreamReader(url.openStream()))
                    val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
                    var tagName = jsonObject["tag_name"].asString
                    tagName = tagName.removePrefix("v")
                    if (curVer.compareTo(plugVer) <= 0) {
                        player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> <green>The plugin is up to date!</green>"))
                    } else {
                        Bukkit.getScheduler().runTaskLater(Main.INSTANCE, Runnable {
                            updateVersion.thenApply { version ->
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> update available: <green>$version</green>"))
                                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> download at <aqua><click:open_url:'https://github.com/byPixelTV/SkCloudnet/releases'>https://github.com/byPixelTV/SkCloudnet/releases</click></aqua>"))
                                true
                            }
                        }, 30)
                    }
                }
            }
        }
        literalArgument("reload") {
            withPermission("skcloudnet.admin.reload")
            anyExecutor { player, _ ->
                Main.INSTANCE.reloadConfig()
                val path = Paths.get("/plugins/SkCloudnet/config.yml")
                if (Files.exists(path)) {
                    Main.INSTANCE.saveConfig()
                } else {
                    Main.INSTANCE.saveDefaultConfig()
                }
                player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> <color:#43fa00>Successfully reloaded the config!</color>"))
            }
        }
        literalArgument("kick") {
            literalArgument("self") {
                playerExecutor { player, _ ->
                    playerManager?.playerExecutor(player.uniqueId)?.kick(literalText("Test"))
                }
            }
            literalArgument("others_test_1") {
                stringArgument("player", false) {
                    replaceSuggestions(ArgumentSuggestions.stringCollection {
                        playerManager?.onlinePlayers()?.uniqueIds()?.map { it.toString() } ?: emptyList()
                    })
                    greedyStringArgument("reason", true) {
                        playerExecutor { player, args ->
                            val reason = args.getOptional(1).getOrNull() as? String ?: "No reason specified"
                            val target = UUID.fromString(args[0].toString())
                            playerManager?.playerExecutor(target)?.kick(literalText(reason))
                            player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> <grey>Kicked player <aqua>${Bukkit.getPlayer(target)?.name}</aqua> from the network!</grey>"))
                        }
                    }
                }
            }
            literalArgument("others_test_2") {
                stringArgument("player", false) {
                    replaceSuggestions(ArgumentSuggestions.stringCollection {
                        playerManager?.onlinePlayers()?.uniqueIds()?.map { it.toString() } ?: emptyList()
                    })
                    greedyStringArgument("reason", true) {
                        playerExecutor { player, args ->
                            val reason = args.getOptional(0).getOrNull() as? String ?: "No reason specified"
                            val target = UUID.fromString(args[0].toString())
                            playerManager?.playerExecutor(target)?.kick(literalText(reason))
                            player.sendMessage(miniMessages.deserialize("<dark_grey>[<gradient:aqua:blue:aqua>SkCloudnet</gradient>]</dark_grey> <grey>Kicked player <aqua>${Bukkit.getPlayer(target)?.name}</aqua> from the network!</grey>"))
                        }
                    }
                }
            }
        }
    }
}