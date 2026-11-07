package dev.kuntal.cockpit.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Collects [this] into [sink] until the returned job is cancelled: the test's stand-in for "the screen is started / stopped". */
fun <T> Flow<T>.collectInto(scope: CoroutineScope, sink: MutableList<T>): Job = scope.launch { collect { sink += it } }
