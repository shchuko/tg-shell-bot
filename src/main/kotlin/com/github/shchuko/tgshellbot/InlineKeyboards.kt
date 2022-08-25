package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton

object InlineKeyboards {
    fun runningProcActionsKeyboard() = InlineKeyboardMarkup.createSingleButton(
        InlineKeyboardButton.CallbackData("Terminate", "terminateExecution"),
    )
}