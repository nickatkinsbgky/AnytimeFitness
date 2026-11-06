package com.nickatkins.anytimefitness

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class ClubOsClient {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun fetchWhosInClub(username: String, password: String): Result<String> {
        return try {
            // Step 1: GET login page to establish session
            val loginPageReq = Request.Builder()
                .url("https://app.club-os.com/action/Login/view")
                .get()
                .build()
            client.newCall(loginPageReq).execute().use { /* just to set cookies */ }

            // Step 2: POST credentials (common field names; adjust if Club OS uses different ones)
            val form = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("j_username", username) // fallback common names
                .add("j_password", password)
                .build()

            val loginReq = Request.Builder()
                .url("https://app.club-os.com/action/Login/login")
                .post(form)
                .build()

            val loginResponse = client.newCall(loginReq).execute()
            val loginBody = loginResponse.body?.string() ?: ""

            if (loginResponse.code >= 400 || loginBody.contains("invalid", ignoreCase = true) ||
                loginBody.contains("incorrect", ignoreCase = true)
            ) {
                return Result.failure(Exception("Login failed — check username/password"))
            }

            // Step 3: GET dashboard
            val dashReq = Request.Builder()
                .url("https://app.club-os.com/action/Dashboard/view")
                .get()
                .build()

            val dashResponse = client.newCall(dashReq).execute()
            val html = dashResponse.body?.string() ?: ""

            if (html.contains("Login", ignoreCase = true) && html.contains("password", ignoreCase = true)) {
                return Result.failure(Exception("Still on login page — credentials may be wrong"))
            }

            // Step 4: Parse "Who's in the club" section
            val names = parseWhosInClub(html)
            if (names.isEmpty()) {
                Result.success("No one currently checked in\n(or section not found)")
            } else {
                Result.success(names.joinToString("\n"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseWhosInClub(html: String): List<String> {
        val doc = Jsoup.parse(html)

        // Try several possible selectors / text patterns common in Club OS dashboards
        val candidates = mutableListOf<String>()

        // Look for a section titled something like "Who's in the club" or "Currently in Club"
        val section = doc.select("*:contains(Who's in the club), *:contains(Who’s in the club), *:contains(Currently in), *:contains(In the Club)")
            .firstOrNull()?.parent()

        if (section != null) {
            section.select("li, .name, .member-name, td, span").forEach { el ->
                val text = el.text().trim()
                if (text.length in 3..40 && text.matches(Regex("[A-Za-z\s.'-]+"))) {
                    candidates.add(text)
                }
            }
        }

        // Fallback: grab any short name-like strings near the dashboard
        if (candidates.isEmpty()) {
            doc.select("div, li, span, td").forEach { el ->
                val text = el.ownText().trim()
                if (text.length in 4..30 && text.matches(Regex("[A-Z][a-z]+(?:\s[A-Z][a-z]+){0,2}"))) {
                    candidates.add(text)
                }
            }
        }

        return candidates.distinct().take(15)
    }
}
