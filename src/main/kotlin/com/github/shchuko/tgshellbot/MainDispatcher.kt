package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import java.util.*

class MainDispatcher(private val config: Config) {
    private val messageIdToProcessMap = mutableMapOf<Long, ProcessWrapper>()
    private val logger = this::class.logger()

    fun useIn(dispatcher: Dispatcher) = with(dispatcher) {
        command("exec", this@MainDispatcher::handleExecCommand)
        callbackQuery("terminateExecution", this@MainDispatcher::handleTerminateExecutionCbQuery)
        // TODO automate refresh - execute task in background every 3 seconds for all processes in [messageIdToProcessMap]
        callbackQuery("refreshStdoutStderr", this@MainDispatcher::handleRefreshStdoutStderr)
    }

    private fun handleExecCommand(env: CommandHandlerEnvironment): Unit = with(env) {
        val chatId = message.chat.id
        val commandArgs = listOf(
            config.shellPath, "-c", args.joinToString(" ")
        )

        val process = try {
            ProcessBuilder(commandArgs)
                .directory(getUserHome().toFile())
                .start()
        } catch (e: Exception) {
            bot.sendMessage(
                chatId = ChatId.fromId(chatId),
                text = MessageTemplates.processCouldNotStartMessage(commandArgs, e),
                parseMode = MessageTemplates.parseMode,
                replyToMessageId = message.messageId
            )
            return
        }

        val reply = bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            replyToMessageId = message.messageId,
            text = MessageTemplates.processStartedMessage(commandArgs),
            parseMode = MessageTemplates.parseMode,
            replyMarkup = InlineKeyboards.runningProcActionsKeyboard()
        )

        if (!reply.isSuccess) {
            logger.error("Failed to reply to message ${message.messageId}")
            process.destroy() // TODO ensure destroyed?
            return
        }
        messageIdToProcessMap[reply.get().messageId] = ProcessWrapper(commandArgs, process)
    }

    private fun handleTerminateExecutionCbQuery(env: CallbackQueryHandlerEnvironment): Unit = with(env) {
        val messageId = callbackQuery.message?.messageId ?: return
        val chatId = callbackQuery.message?.chat?.id ?: return

        findProcessInMapOrShowAlert(this, true).ifPresent { pw ->
            val stdout = pw.process.inputStream.readAvailableString()
            val stderr = pw.process.errorStream.readAvailableString()

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = MessageTemplates.processStdoutStderrMessage(
                    pw.commandArgs,
                    if (pw.process.isAlive) "will be terminated by user request" else "execution finished",
                    pw.stdoutSb.append(stdout).toString(),
                    pw.stderrSb.append(stderr).toString()
                ),
                parseMode = MessageTemplates.parseMode,
                replyMarkup = null
            )

            if (pw.process.isAlive) {
                pw.process.destroy()  // TODO ensure destroyed?
            }
        }

    }

    private fun handleRefreshStdoutStderr(env: CallbackQueryHandlerEnvironment): Unit = with(env) {
        val messageId = callbackQuery.message?.messageId ?: return
        val chatId = callbackQuery.message?.chat?.id ?: error("chat id is null")


        findProcessInMapOrShowAlert(this, false).ifPresent { pw ->
            val stdout = pw.process.inputStream.readAvailableString()
            val stderr = pw.process.errorStream.readAvailableString()

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = MessageTemplates.processStdoutStderrMessage(
                    pw.commandArgs,
                    if (pw.process.isAlive) "running" else "execution finished",
                    pw.stdoutSb.append(stdout).toString(),
                    pw.stderrSb.append(stderr).toString()
                ),
                parseMode = MessageTemplates.parseMode,
                replyMarkup = if (pw.process.isAlive) InlineKeyboards.runningProcActionsKeyboard() else null
            )
        }
    }

    private fun findProcessInMapOrShowAlert(
        env: CallbackQueryHandlerEnvironment,
        removeFound: Boolean
    ): Optional<ProcessWrapper> = with(env) {
        val messageId = callbackQuery.message?.messageId ?: return Optional.empty()
        val chatId = callbackQuery.message?.chat?.id ?: return Optional.empty()

        if (!messageIdToProcessMap.containsKey(messageId)) {
            bot.answerCallbackQuery(
                callbackQueryId = callbackQuery.id,
                text = MessageTemplates.processControlIsLostMessage(),
                showAlert = true
            )
            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                replyMarkup = null
            )
            return Optional.empty()
        }

        return Optional.of(
            if (removeFound)
                messageIdToProcessMap.remove(messageId)!!
            else
                messageIdToProcessMap[messageId]!!
        )
    }

    private data class ProcessWrapper(
        val commandArgs: List<String>,
        val process: Process,
        val stdoutSb: StringBuilder = StringBuilder(),
        val stderrSb: StringBuilder = StringBuilder()
    )
}
