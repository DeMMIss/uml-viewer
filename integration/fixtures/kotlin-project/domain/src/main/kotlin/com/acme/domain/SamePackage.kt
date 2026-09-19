package com.acme.domain

interface SamePackage

class SameUser(val dependency: SamePackage)

class Result

class BuiltinNameUser(val result: Result)

class Item

class T

class TypeParameterUser<Item>(val item: Item) {
    fun <T> echo(value: T): T = value
}

class GenericOuter<Item> {
    inner class Inner(val item: Item)
}
