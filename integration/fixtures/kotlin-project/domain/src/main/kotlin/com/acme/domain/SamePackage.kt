package com.acme.domain

interface SamePackage

class SameUser(val dependency: SamePackage)

class Result

class BuiltinNameUser(val result: Result)

class Item

class T

class TypeParameterUser<Item>(val item: Item) {
    fun <T> echo(value: T): T = value

    val <T> List<T>.second: T get() = this[1]
}

val <T> List<T>.firstValue: T get() = first()

class GenericOuter<Item> {
    inner class Inner(val item: Item)
}
