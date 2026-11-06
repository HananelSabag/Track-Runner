package com.hananel.fast.mobile.ui

import com.hananel.fast.mobile.theme.FastColors

/**
 * One colour for one meaning, the same on the phone and the watch (D45):
 * waiting grey, open/ready blue, live/measuring/saved green, blocked red with a lock.
 * Rose without a lock stays a sprint, a peak, a doubtful result.
 */
object StatusColors {
    val Waiting = FastColors.Mist
    val Ready = FastColors.Sky
    val Live = FastColors.Mint
    val Blocked = FastColors.RoseDeep
}
