package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.Dispatcher
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MainDispatcher(private val config: Config) {
    /**
     * Key: `Pair<ChatId, MessageId>`
     *
     * Value: `ProcessWrapper`
     */
    private val processStorage = ConcurrentHashMap<Pair<Long, Long>, ProcessWrapper>()

    /**
     * Key: `ChatId`
     *
     * Value: `Milliseconds` - last sent message's System.currentTimeMillis()
     */
    private val lastSentChatMessageTime = ConcurrentHashMap<Long, Long>()

    private val logger = this::class.logger()
    private val threadPool = ScheduledThreadPoolExecutor(4)

    fun useIn(dispatcher: Dispatcher) = with(dispatcher) {
        command("start") {
            threadPool.execute { handleStartCommand(this) }
        }

        command("exec") {
            // Internal calls may retry with sleeping a thread, schedule on thread pool to prevent blocking
            threadPool.execute { handleExecCommand(this) }
        }

        callbackQuery("terminateExecution") {
            // Internal calls may retry with sleeping a thread, schedule on thread pool to prevent blocking
            threadPool.execute { handleTerminateExecutionCbQuery(this) }
        }
    }

    private fun handleStartCommand(env: CommandHandlerEnvironment): Unit = with(env) {
        val chatId = message.chat.id
        if (rescheduleIfRequired(chatId) { handleExecCommand(env) }) {
            return
        }

        if (!findUserOrReplyNotAllowed(message, bot)) {
            return
        }

        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = MessageTemplates.startMessage(config),
            parseMode = MessageTemplates.parseMode,
            replyToMessageId = message.messageId
        )
    }

    private fun handleExecCommand(env: CommandHandlerEnvironment): Unit = with(env) {
        val chatId = message.chat.id
        if (rescheduleIfRequired(chatId) { handleExecCommand(env) }) {
            return
        }

        if (!findUserOrReplyNotAllowed(message, bot)) {
            return
        }

        val commandArgs = listOf(
            config.shellPath, "-c", args.joinToString(" ")
        )

        val process = try {
            ProcessBuilder(commandArgs).directory(getUserHome().toFile()).start()
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


        if (reply.isError) {
            logger.error("Failed to reply to message ${message.messageId}")
            process.destroy() // TODO ensure destroyed?
            return@with
        }

        processStorage[chatId to reply.get().messageId] = ProcessWrapper(commandArgs, process)
        refreshProcessStatus(chatId, reply.get().messageId, bot)
    }

    private fun handleTerminateExecutionCbQuery(env: CallbackQueryHandlerEnvironment): Unit = with(env) {
        val chatId = callbackQuery.message?.chat?.id ?: return
        if (rescheduleIfRequired(chatId) { handleTerminateExecutionCbQuery(env) }) {
            return
        }

        if (!findUserOrReplyNotAllowed(callbackQuery, bot)) {
            return
        }
        val messageId = callbackQuery.message?.messageId ?: return

        findProcessInMapAndProceed(chatId, messageId, true, bot, callbackQuery) { pw ->
            val stdout = pw.process.inputStream.readAvailableString()
            val stderr = pw.process.errorStream.readAvailableString()
            val isAlive = pw.process.isAlive

            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = MessageTemplates.processStdoutStderrMessage(
                    pw.commandArgs,
                    if (isAlive) "will be terminated by user request" else "execution finished",
                    pw.stdoutSb.append(stdout).toString(),
                    pw.stderrSb.append(stderr).toString()
                ),
                parseMode = MessageTemplates.parseMode,
                replyMarkup = null
            )

            if (isAlive) {
                pw.process.destroy()  // TODO ensure destroyed?
            }
        }

    }

    private fun refreshProcessStatus(chatId: Long, messageId: Long, bot: Bot) {
        if (rescheduleIfRequired(chatId) { refreshProcessStatus(chatId, messageId, bot) }) {
            return
        }

        findProcessInMapAndProceed(chatId, messageId, false, bot, null) { pw ->
            val stdout = pw.process.inputStream.readAvailableString()
            val stderr = pw.process.errorStream.readAvailableString()

            val isAlive = pw.process.isAlive
            bot.editMessageText(
                chatId = ChatId.fromId(chatId),
                messageId = messageId,
                text = MessageTemplates.processStdoutStderrMessage(
                    pw.commandArgs,
                    if (isAlive) "running" else "execution finished",
                    pw.stdoutSb.append(stdout).toString(),
                    pw.stderrSb.append(stderr).toString()
                ),
                parseMode = MessageTemplates.parseMode,
                replyMarkup = if (isAlive) InlineKeyboards.runningProcActionsKeyboard() else null
            )


            if (isAlive) {
                threadPool.schedule({
                    refreshProcessStatus(chatId, messageId, bot)
                }, 2, TimeUnit.SECONDS)
            } else {
                processStorage.remove(chatId to messageId)
            }
        }
    }

    private fun findProcessInMapAndProceed(
        chatId: Long,
        messageId: Long,
        removeFound: Boolean,
        bot: Bot,
        callbackQuery: CallbackQuery?,
        pwProcessor: (ProcessWrapper) -> Unit
    ) {
        val sk = chatId to messageId

        val found = processStorage.getOrDefault(sk, null)
        if (found == null) {
            if (callbackQuery != null) {
                bot.answerCallbackQuery(
                    callbackQueryId = callbackQuery.id,
                    text = MessageTemplates.processControlIsLostMessage(),
                    showAlert = true
                )

            }
            bot.editMessageReplyMarkup(
                chatId = ChatId.fromId(chatId), messageId = messageId, replyMarkup = null
            )

            return
        }

        found.lock.withLock {
            // Check whether previous lock holder not destroyed the process
            if (!processStorage.containsKey(sk)) {
                return
            }

            pwProcessor(found)
            if (removeFound) {
                processStorage.remove(sk)
            }
        }
    }

    private fun findUserOrReplyNotAllowed(message: Message, bot: Bot): Boolean {
        if (message.chat.username in config.telegramAllowedUsers) {
            return true
        }

        bot.sendMessage(
            chatId = ChatId.fromId(message.chat.id),
            text = MessageTemplates.notAllowedMessage(),
            parseMode = MessageTemplates.parseMode,
            replyToMessageId = message.messageId
        )

        return false
    }

    private fun findUserOrReplyNotAllowed(callbackQuery: CallbackQuery, bot: Bot): Boolean {
        val message = callbackQuery.message ?: return false

        if (message.chat.username in config.telegramAllowedUsers) {
            return true
        }

        bot.editMessageReplyMarkup(
            chatId = ChatId.fromId(message.chat.id), messageId = message.messageId, replyMarkup = null
        )

        bot.answerCallbackQuery(
            callbackQueryId = callbackQuery.id, text = MessageTemplates.notAllowedMessage(), showAlert = true
        )
        return false
    }

    private data class ProcessWrapper(
        val commandArgs: List<String>,
        val process: Process,
        val stdoutSb: StringBuilder = StringBuilder(),
        val stderrSb: StringBuilder = StringBuilder(),
        val lock: ReentrantLock = ReentrantLock()
    )

    /**
     * @return true if rescheduled
     */
    private fun rescheduleIfRequired(chatId: Long, rescheduleBlock: () -> Unit): Boolean {
        /*
         * TODO use priority queue within thread pool to reschedule tasks:
         * TODO command replies are more important than message refreshes
         *
         * From Telegram FAQ:
         * When sending messages inside a particular chat, avoid sending more than one message per second.
         * We may allow short bursts that go over this limit, but eventually you'll begin receiving 429 errors.
         *
         * Let's increase limit to 1100 ms > 1s
         */
        val currentTime = System.currentTimeMillis()
        val timeout = lastSentChatMessageTime.getOrPut(chatId) { -1100 } - currentTime + 1100
        if (timeout > 0) {
            // Re-schedule self
            threadPool.schedule({ rescheduleBlock() }, timeout, TimeUnit.MILLISECONDS)
            logger.info("Re-schedule task for chatId=$chatId, repeat in ${timeout}ms")
            return true
        }
        // Or register
        lastSentChatMessageTime[chatId] = currentTime
        return false
    }
}
