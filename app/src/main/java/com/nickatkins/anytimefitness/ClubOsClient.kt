package com.nickatkins.anytimefitness

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

data class ClubMember(
    val name: String,
    val photoUrl: String? = null
)

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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun fetchWhosInClub(username: String, password: String): Result<List<ClubMember>> {
        return try {
            // Step 1: GET login page and parse the form
            val loginPageReq = Request.Builder()
                .url("https://app.club-os.com/action/Login/view")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()

            val loginPageResponse = client.newCall(loginPageReq).execute()
            val loginHtml = loginPageResponse.body?.string() ?: ""

            if (loginHtml.isBlank()) {
                return Result.failure(Exception("Could not load Club OS login page"))
            }

            val doc = Jsoup.parse(loginHtml)
            val form = doc.select("form").firstOrNull()
                ?: return Result.failure(Exception("No login form found on page"))

            val action = form.attr("action").ifBlank { "https://app.club-os.com/action/Login/login" }
            val actionUrl = if (action.startsWith("http")) action else "https://app.club-os.com$action"

            // Collect all form fields, override username/password ones
            val formBuilder = FormBody.Builder()
            form.select("input").forEach { input ->
                val name = input.attr("name")
                if (name.isBlank()) return@forEach
                val value = input.attr("value")
                when {
                    name.contains("user", ignoreCase = true) || name == "username" || name == "j_username" ->
                        formBuilder.add(name, username)
                    name.contains("pass", ignoreCase = true) || name == "password" || name == "j_password" ->
                        formBuilder.add(name, password)
                    else -> formBuilder.add(name, value)
                }
            }

            // Fallback if no matching fields were found
            if (form.select("input[name*=user], input[name*=User], input[name=username], input[name=j_username]").isEmpty()) {
                formBuilder.add("username", username)
                formBuilder.add("password", password)
            }

            // Step 2: POST the login
            val loginReq = Request.Builder()
                .url(actionUrl)
                .post(formBuilder.build())
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://app.club-os.com/action/Login/view")
                .header("Origin", "https://app.club-os.com")
                .build()

            val loginResponse = client.newCall(loginReq).execute()
            val loginBody = loginResponse.body?.string() ?: ""

            // Better success detection: if we still see a login form or error text, fail
            val stillLogin = loginBody.contains("password", ignoreCase = true) &&
                    (loginBody.contains("Login", ignoreCase = true) || loginBody.contains("Sign in", ignoreCase = true))
            val hasError = loginBody.contains("invalid", ignoreCase = true) ||
                    loginBody.contains("incorrect", ignoreCase = true) ||
                    loginBody.contains("failed", ignoreCase = true)

            if (loginResponse.code >= 400 || stillLogin || hasError) {
                return Result.failure(Exception("Login failed — check username/password (or Club OS form changed)"))
            }

            // Step 3: GET dashboard
            val dashReq = Request.Builder()
                .url("https://app.club-os.com/action/Dashboard/view")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()

            val dashResponse = client.newCall(dashReq).execute()
            val html = dashResponse.body?.string() ?: ""

            if (html.contains("Login", ignoreCase = true) && html.contains("password", ignoreCase = true)) {
                return Result.failure(Exception("Still on login page after login attempt"))
            }

            // Step 4: Parse "Who's in the club" section
            val members = parseWhosInClub(html)
            Result.success(members)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Keep a string version for the widget
    fun fetchWhosInClubText(username: String, password: String): Result<String> {
        return fetchWhosInClub(username, password).map { members ->
            if (members.isEmpty()) {
                "No one currently checked in\n(or section not found)"
            } else {
                members.joinToString("\n") { it.name }
            }
        }
    }

    private fun parseWhosInClub(html: String): List<ClubMember> {
        val doc = Jsoup.parse(html)
        val members = mutableListOf<ClubMember>()

        val section = doc.select("*:contains(Who's in the club), *:contains(Who’s in the club), *:contains(Currently in), *:contains(In the Club)")
            .firstOrNull()?.parent()

        val searchRoot = section ?: doc.body()

        searchRoot.select("div, li, tr, .member, .checkin, .attendee").forEach { el ->
            val name = extractName(el)
            if (name != null) {
                val photo = extractPhotoUrl(el)
                members.add(ClubMember(name, photo))
            }
        }

        if (members.isEmpty()) {
            searchRoot.select("li, .name, .member-name, td, span").forEach { el ->
                val text = el.text().trim()
                if (text.length in 3..40 && text.matches(Regex("[A-Za-z\\s.'-]+"))) {
                    members.add(ClubMember(text))
                }
            }
        }

        return members.distinctBy { it.name }.take(20)
    }

    private fun extractName(el: Element): String? {
        val text = el.ownText().trim().ifEmpty { el.select(".name, .member-name, span, a").firstOrNull()?.text()?.trim() }
            ?: el.text().trim()

        return if (text.length in 3..40 && text.matches(Regex("[A-Za-z\\s.'-]+"))) {
            text
        } else {
            null
        }
    }

    private fun extractPhotoUrl(el: Element): String? {
        val img = el.select("img").firstOrNull()
        val src = img?.attr("src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }

        return src?.let { url ->
            if (url.startsWith("http")) url
            else if (url.startsWith("/")) "https://app.club-os.com$url"
            else null
        }
    }
}
