package com.github.shchuko.tgshellbot

import com.github.kotlintelegrambot.entities.ParseMode
import java.io.PrintWriter
import java.io.StringWriter

object MessageTemplates {
    val parseMode = ParseMode.MARKDOWN_V2

    fun processCouldNotStartMessage(commandArgs: List<String>, cause: Exception) = buildString {
        appendMarkdownCodeBlock("Execute", emptyLineEnd = true) { appendCodeLine(commandArgs) }
        appendStatus("could not start", emptyLineEnd = true)
        appendMarkdownCodeBlock("Cause", emptyLineEnd = false) {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    cause.printStackTrace(pw)
                }
                appendCode(sw.toString())
            }
        }
    }

    fun processStartedMessage(commandArgs: List<String>) = buildString {
        appendMarkdownCodeBlock("Execute", emptyLineEnd = true) { appendCodeLine(commandArgs) }
        appendStatus("running", emptyLineEnd = false)
    }

    fun processControlIsLostMessage() = buildString {
        appendLine("Subprocess control lost: cannot terminate")
        appendLine(" ")
        appendLine("Executed subprocess is no more bot process child: may be caused by bot restart")
    }

    fun processStdoutStderrMessage(
        commandArgs: List<String>,
        status: String,
        stdout: String,
        stderr: String
    ) = buildString {
        appendMarkdownCodeBlock("Execute", emptyLineEnd = true) { appendCodeLine(commandArgs.toString()) }
        appendStatus(status, emptyLineEnd = true)
        appendMarkdownCodeBlock("stdout", emptyLineEnd = false) { appendCodeLine(stdout) }
        appendMarkdownCodeBlock("stderr", emptyLineEnd = false) { appendCodeLine(stderr) }
    }

    fun notAllowedMessage() = "Operation not allowed"

    /**
     * name:
     * ```
     * codeContent
     * codeContent
     * codeContent
     * ```
     * optionalEmptyLine
     */
    private fun StringBuilder.appendMarkdownCodeBlock(
        name: String,
        emptyLineEnd: Boolean,
        codeContent: StringBuilder.() -> Unit
    ) {
        append(name)
        appendLine(":")
        appendLine("```")
        codeContent()
        appendLine("```")
        if (emptyLineEnd) {
            appendLine()
        }
    }

    private fun StringBuilder.appendStatus(status: String, emptyLineEnd: Boolean) {
        append("Status: *_")
        append(status)
        appendLine("_*")
        if (emptyLineEnd) {
            appendLine()
        }
    }

    private fun <T> StringBuilder.appendCode(any: T) = append(any.toString().withMarkdownCodeRequiredShielding)
    private fun <T> StringBuilder.appendCodeLine(any: T) = appendLine(any.toString().withMarkdownCodeRequiredShielding)
    private val String.withMarkdownCodeRequiredShielding: String
        get() = replace("\\", "\\\\").replace("`", "\\`")
}