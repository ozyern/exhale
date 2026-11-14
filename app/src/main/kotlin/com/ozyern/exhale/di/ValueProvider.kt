package com.ozyern.exhale.di

/** Small lazy dependency handle for keeping Android composition boundaries lazy under Koin. */
fun interface ValueProvider<out T> {
    fun get(): T
}
