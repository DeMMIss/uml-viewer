package com.acme.app

import com.acme.data.impl.PluginImpl
import com.acme.domain.*
import com.acme.domain.Outer.Nested as NestedAlias
import javax.inject.Inject

class Consumer @Inject constructor(
    val plugins: Set<@JvmSuppressWildcards Plugin>,
    val implementation: PluginImpl,
    val nested: NestedAlias,
)

annotation class Special

class QualifiedConsumer @Inject constructor(
    @Special val plugins: Set<Plugin>,
)
