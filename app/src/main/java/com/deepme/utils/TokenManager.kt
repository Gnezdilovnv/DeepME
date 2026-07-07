package com.deepme.utils

import android.content.Context

object TokenManager {
    private const val PREFS = "deepme_tokens"

    fun saveTokens(ctx: Context, deepSeekKey: String, githubToken: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("deepseek_key", deepSeekKey)
            .putString("github_token", githubToken)
            .apply()
        Logger.log("Tokens saved")
    }

    fun getDeepSeekKey(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("deepseek_key", "") ?: ""
    }

    fun getGitHubToken(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("github_token", "") ?: ""
    }

    fun isAuthorized(ctx: Context): Boolean {
        return getDeepSeekKey(ctx).isNotEmpty() && getGitHubToken(ctx).isNotEmpty()
    }
}