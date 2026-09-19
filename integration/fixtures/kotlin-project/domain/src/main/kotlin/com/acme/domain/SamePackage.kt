package com.acme.domain

interface SamePackage

class SameUser(val dependency: SamePackage)
