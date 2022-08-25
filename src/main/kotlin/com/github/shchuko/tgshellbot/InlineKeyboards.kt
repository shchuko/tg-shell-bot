package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

object InlineKeyboards {
    fun runningProcActionsKeyboard() = InlineKeyboardMarkup.createSingleRowKeyboard(
        InlineKeyboardButton.CallbackData("Terminate", "terminateExecution"),
        InlineKeyboardButton.CallbackData("Refresh stdout&stderr", "refreshStdoutStderr"),
    )
}