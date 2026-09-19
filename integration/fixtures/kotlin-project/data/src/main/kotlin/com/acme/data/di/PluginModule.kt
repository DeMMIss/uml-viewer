package com.acme.data.di

import com.acme.data.impl.PluginImpl
import com.acme.domain.Plugin as Contract
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

@Module
abstract class PluginModule {
    @Binds
    @IntoSet
    abstract fun bindPlugin(implementation: PluginImpl): Contract
}
