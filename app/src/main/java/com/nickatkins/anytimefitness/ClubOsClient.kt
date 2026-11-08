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

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                cookies.forEach { newCookie ->
                    list.removeAll { it.name == newCookie.name }
                    list.add(newCookie)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun fetchWhosInClub(username: String, password: String): Result<List<ClubMember>> {
        return try {
            // Step 1: GET login page
            val loginPageReq = Request.Builder()
                .url("https://app.club-os.com/action/Login/view")
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val loginPageResponse = client.newCall(loginPageReq).execute()
            val loginHtml = loginPageResponse.body?.string() ?: ""

            if (loginHtml.isBlank()) {
                return Result.failure(Exception("Could not load Club OS login page"))
            }

            val doc = Jsoup.parse(loginHtml)
            val form = doc.select("form#loginForm").firstOrNull()
                ?: return Result.failure(Exception("No login form found"))

            val action = form.attr("action").ifBlank { "/action/Login" }
            val actionUrl = if (action.startsWith("http")) action else "https://app.club-os.com$action"

            val formBuilder = FormBody.Builder()
            form.select("input[name]").forEach { input ->
                val name = input.attr("name")
                val value = input.attr("value")
                when {
                    name.equals("username", ignoreCase = true) -> formBuilder.add(name, username)
                    name.equals("password", ignoreCase = true) -> formBuilder.add(name, password)
                    else -> formBuilder.add(name, value)
                }
            }
            formBuilder.add("login", "Submit")

            // Step 2: POST login (no auto-redirect)
            val loginReq = Request.Builder()
                .url(actionUrl)
                .post(formBuilder.build())
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://app.club-os.com/action/Login/view")
                .header("Origin", "https://app.club-os.com")
                .build()

            val loginResponse = client.newCall(loginReq).execute()
            val loginBody = loginResponse.body?.string() ?: ""

            var dashUrl = "https://app.club-os.com/action/Dashboard/view"
            if (loginResponse.isRedirect) {
                val location = loginResponse.header("Location")
                if (!location.isNullOrBlank()) {
                    dashUrl = if (location.startsWith("http")) location else "https://app.club-os.com$location"
                }
            }

            if (loginBody.contains("id=\"loginForm\"") && !loginResponse.isRedirect) {
                return Result.failure(Exception("Login failed — still on login page. Check credentials or try again."))
            }

            // Step 3: GET dashboard
            val dashReq = Request.Builder()
                .url(dashUrl)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://app.club-os.com/action/Login/view")
                .build()

            val dashResponse = client.newCall(dashReq).execute()
            val html = dashResponse.body?.string() ?: ""

            if (html.contains("id=\"loginForm\"") || html.contains("placeholder=\"Password\"")) {
                return Result.failure(Exception("Could not reach dashboard after login (session not established)"))
            }

            val members = parseWhosInClub(html)
            Result.success(members)
        } catch (e: Exception) {
            Result.failure(Exception("Login/network error: ${e.message ?: "unknown"}"))
        }
    }

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

        // Look for a more specific container around the "Who's in the club" heading
        var searchRoot: Element? = null
        val candidates = doc.select("h1, h2, h3, h4, h5, h6, .widget-title, .panel-title, .box-title, div, span")
        for (el in candidates) {
            val text = el.ownText().trim().lowercase()
            if ((text.contains("who") && text.contains("club")) ||
                text.contains("currently in the club") ||
                text.contains("in the club now")
            ) {
                // Prefer the closest reasonable parent that isn't the whole page
                var parent = el.parent()
                var hops = 0
                while (parent != null && hops < 4) {
                    if (parent.select("li, .member, .checkin, .attendee, .person").isNotEmpty()) {
                        searchRoot = parent
                        break
                    }
                    parent = parent.parent()
                    hops++
                }
                if (searchRoot == null) searchRoot = el.parent()
                break
            }
        }

        val root = searchRoot ?: doc.body()

        // Prefer list-like or member-like elements
        root.select("li, .member, .checkin, .attendee, .person, .user, tr").forEach { el ->
            val name = extractName(el)
            if (name != null && !isNavigationText(name)) {
                val photo = extractPhotoUrl(el)
                members.add(ClubMember(name, photo))
            }
        }

        // Fallback: tighter text nodes only if nothing better was found
        if (members.isEmpty()) {
            root.select(".name, .member-name, span, a").forEach { el ->
                val text = el.ownText().trim()
                if (text.length in 3..40 && text.matches(Regex("[A-Za-z\\s.'-]+")) && !isNavigationText(text)) {
                    members.add(ClubMember(text))
                }
            }
        }

        return members.distinctBy { it.name }.take(15)
    }

    private fun isNavigationText(text: String): Boolean {
        val lower = text.lowercase()
        val navWords = listOf(
            "dashboard", "calendar", "reports", "settings", "logout", "login",
            "members", "prospects", "follow", "tasks", "messages", "home",
            "who", "club", "view", "add", "search", "menu", "profile"
        )
        return navWords.any { lower.contains(it) } || text.length < 4
    }

    private fun extractName(el: Element): String? {
        val candidates = listOf(
            el.select(".name, .member-name, .person-name").firstOrNull()?.ownText(),
            el.ownText(),
            el.select("span, a").firstOrNull()?.ownText()
        )
        val text = candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null

        return if (text.length in 3..40 && text.matches(Regex("[A-Za-z\\s.'-]+"))) text else null
    }

    private fun extractPhotoUrl(el: Element): String? {
        val img = el.select("img").firstOrNull()
        val src = img?.attr("src")?.takeIf { it.isNotBlank() && !it.contains("logo", ignoreCase = true) && !it.contains("icon", ignoreCase = true) }
            ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }

        return src?.let { url ->
            if (url.startsWith("http")) url
            else if (url.startsWith("/")) "https://app.club-os.com$url"
            else null
        }
    }
}
