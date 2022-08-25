package com.github.shchuko.tgshellbot

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject


fun <T : KClass<*>> T.logger(): Logger = java.logger()
fun <T : Class<*>> T.logger(): Logger = LoggerFactory.getLogger(getClassForLogging(this))

fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> {
    return javaClass.enclosingClass?.takeIf {
        it.kotlin.companionObject?.java == javaClass
    } ?: javaClass
}