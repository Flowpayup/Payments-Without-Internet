// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.payment

import android.content.ClipData

/** Check both the per-copy identity and its content before clearing a clip. */
internal fun ownsVpaClip(clip: ClipData, vpa: String?, token: String?): Boolean =
    vpa != null && token != null && clip.description.label?.toString() == "Flowpay VPA $token" &&
        clip.itemCount == 1 && clip.getItemAt(0).text?.toString() == vpa
