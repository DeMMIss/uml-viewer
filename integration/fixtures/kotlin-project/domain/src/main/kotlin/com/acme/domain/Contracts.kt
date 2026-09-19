package com.acme.domain

interface Plugin

object PluginCatalog {
    val default: Plugin? = null
}

class Outer {
    class Nested(val plugin: Plugin)

    fun run(value: String): Plugin = error(value)

    fun run(value: Int): Plugin = error(value)
}

fun topLevel(plugin: Plugin): Plugin = plugin
