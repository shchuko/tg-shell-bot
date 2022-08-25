package com.github.shchuko.tgshellbot

import java.io.File
import java.io.FileInputStream
import java.util.*


data class Config(
    var telegramApiKey: String,
    var telegramAllowedUsers: Set<String>,
    var shellPath: String
) {
    companion object {
        fun fromFile(file: File): Config {
            val properties = Properties().apply {
                FileInputStream(file).use {
                    load(it)
                }
            }

            val telegramApiKey =
                properties.getProperty("telegram.api.key")
                    ?: error("telegram.api.key property is absent")

            val telegramAllowedUsers =
                properties.getProperty("telegram.users.allowed")?.split(",")?.filter { it.isNotBlank() }
                    ?: error("telegram.users.allowed property is absent")

            val shellPath =
                properties.getProperty("shell.path")
                    ?: error("shell.path property is absent")

            return Config(
                telegramApiKey,
                telegramAllowedUsers.toSet(),
                shellPath
            )
        }
    }

    fun loadOptionsOverrides(options: CmdlineOptions) {
        if (options.telegramAllowedUsers.isNotBlank()) {
            telegramAllowedUsers = options.telegramAllowedUsers.split(",").filter { it.isNotBlank() }.toSet()
        }

        if (options.shellPath.isNotBlank()) {
            shellPath = options.shellPath
        }
    }
}
