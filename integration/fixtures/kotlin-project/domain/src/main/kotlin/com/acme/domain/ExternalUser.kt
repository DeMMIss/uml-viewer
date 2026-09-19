package com.acme.domain

import external.Plugin

class ExternalUser(val plugin: Plugin)

class LocalOwner {
    class Plugin

    class User(val plugin: Plugin)
}
