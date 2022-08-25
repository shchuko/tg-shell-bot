package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.logging.LogLevel
import com.sampullara.cli.Args
import java.io.File
import kotlin.system.exitProcess

class Main {
    companion object {
        private val logger = Main::class.logger()

        @JvmStatic
        fun main(args: Array<String>) {
            val options = CmdlineOptions()
            try {
                Args.parse(options, args)
            } catch (e: IllegalArgumentException) {
                logger.error("Invalid program arguments", e)
                exitProcess(1)
            }

            logger.info("Loading config from '${options.configPath}'")
            val config = try {
                Config.fromFile(File(options.configPath)).apply { loadOptionsOverrides(options) }
            } catch (e: Exception) {
                logger.error("Unable to parse config", e)
                exitProcess(1)
            }

            logger.info("config.telegramApiKey=${config.telegramApiKey.take(3)}**********")
            logger.info("config.shellPath=${config.shellPath}")
            logger.info("config.telegramAllowedUsers=${config.telegramAllowedUsers}")


            try {
                val bot = bot {
                    token = config.telegramApiKey
                    logLevel = LogLevel.None
                    dispatch {
                        MainDispatcher(config).useIn(this)
                    }
                }

                bot.startPolling()
            } catch (e: Exception) {
                logger.error("Bot execution error", e)
                exitProcess(1)
            }
        }
    }
}

