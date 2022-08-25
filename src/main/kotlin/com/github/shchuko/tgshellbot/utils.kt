package com.github.shchuko.tgshellbot

import java.io.InputStream
import java.nio.file.Path

fun getUserHome(): Path = Path.of(System.getProperty("user.home"))

fun InputStream.readAvailableString(): String = String(readNBytes(available()))
