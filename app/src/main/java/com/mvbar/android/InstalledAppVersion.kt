package com.mvbar.android

import android.content.Context

/** Read installed metadata rather than a version constant inlined by an older compilation. */
fun installedAppVersion(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
