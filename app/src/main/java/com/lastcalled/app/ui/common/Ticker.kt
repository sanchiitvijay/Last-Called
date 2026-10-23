package com.lastcalled.app.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time now and then every [periodMillis], so "x minutes ago" labels stay fresh. */
fun clockTicks(clock: () -> Long, periodMillis: Long = 30_000): Flow<Long> = flow {
    while (true) {
        emit(clock())
        delay(periodMillis)
    }
}
