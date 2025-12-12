package com.toqsoft.freechat.app

import android.content.Context
import android.content.Intent

object BadgeManager {

    private const val PREF = "badge_pref"
    private const val KEY = "badge_count"

    fun increase(context: Context) {
        val pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val count = pref.getInt(KEY, 0) + 1
        pref.edit().putInt(KEY, count).apply()
        updateBadge(context, count)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY, 0).apply()
        updateBadge(context, 0)
    }

    fun getCount(context: Context): Int {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY, 0)
    }

    private fun updateBadge(context: Context, count: Int) {
        try {
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE")
            intent.putExtra("badge_count", count)
            intent.putExtra("badge_count_package_name", context.packageName)
            intent.putExtra("badge_count_class_name", getLauncherClass(context))
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun getLauncherClass(context: Context): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        for (info in list) {
            if (info.activityInfo.packageName == context.packageName) {
                return info.activityInfo.name
            }
        }
        return null
    }
}
