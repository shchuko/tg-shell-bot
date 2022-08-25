package com.github.shchuko.tgshellbot

import com.sampullara.cli.Argument

class CmdlineOptions {
    @set:Argument(alias = "config", description = "Config path", required = false)
    var configPath: String = getUserHome().resolve(".config/tg-shell-bot/config.property").toString()

    @set:Argument(alias = "allowedUsers", description = "Override allowed users", required = false)
    var telegramAllowedUsers: String = ""

    @set:Argument(alias = "shellPath", description = "Override shell path", required = false)
    var shellPath: String = ""
}