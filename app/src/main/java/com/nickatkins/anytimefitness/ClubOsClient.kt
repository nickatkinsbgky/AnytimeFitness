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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun fetchWhosInClub(username: String, password: String): Result<List<ClubMember>> {
        return try {
            // Step 1: GET login page to establish session
            val loginPageReq = Request.Builder()
                .url("https://app.club-os.com/action/Login/view")
                .get()
                .build()
            client.newCall(loginPageReq).execute().use { /* just to set cookies */ }

            // Step 2: POST credentials
            val form = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("j_username", username)
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

        // Look for a section titled something like "Who's in the club" or "Currently in Club"
        val section = doc.select("*:contains(Who's in the club), *:contains(Who’s in the club), *:contains(Currently in), *:contains(In the Club)")
            .firstOrNull()?.parent()

        val searchRoot = section ?: doc.body()

        // Try to find member-like elements that may contain an image and a name
        searchRoot.select("div, li, tr, .member, .checkin, .attendee").forEach { el ->
            val name = extractName(el)
            if (name != null) {
                val photo = extractPhotoUrl(el)
                members.add(ClubMember(name, photo))
            }
        }

        // Fallback: name-like text if nothing structured was found
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
