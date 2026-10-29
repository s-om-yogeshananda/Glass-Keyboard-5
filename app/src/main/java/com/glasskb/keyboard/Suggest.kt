package com.glasskb.keyboard

import android.content.Context
import org.json.JSONObject

/** আপনি যা টাইপ করেন সেখান থেকে শেখা শব্দ ও শব্দ-জোড়া। শুধু ফোনেই থাকে, কোথাও পাঠানো হয় না। */
class UserDict(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass_dict", Context.MODE_PRIVATE)
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, Int>()
    private var dirty = 0

    init {
        load()
    }

    private fun load() {
        try {
            val w = JSONObject(sp.getString("w", "{}") ?: "{}")
            for (k in w.keys()) words[k] = w.getInt(k)
            val b = JSONObject(sp.getString("b", "{}") ?: "{}")
            for (k in b.keys()) bigrams[k] = b.getInt(k)
        } catch (e: Exception) {
            words.clear()
            bigrams.clear()
        }
    }

    private fun norm(w: String): String = if (w.any { it.code in 0x0980..0x09FF }) w else w.lowercase()

    fun learn(prev: String?, word: String) {
        val w = norm(word)
        if (w.length < 2 || w.any { it.isDigit() }) return
        words[w] = (words[w] ?: 0) + 1
        if (prev != null && prev.isNotEmpty()) {
            val key = norm(prev) + "\t" + w
            bigrams[key] = (bigrams[key] ?: 0) + 1
        }
        dirty++
        if (dirty >= 25) save()
    }

    private fun prune(m: HashMap<String, Int>, limit: Int) {
        if (m.size <= limit) return
        val sorted = m.entries.sortedByDescending { it.value }
        val keep = sorted.take(limit * 3 / 4).map { it.key }.toHashSet()
        val it = m.keys.iterator()
        while (it.hasNext()) if (!keep.contains(it.next())) it.remove()
    }

    fun save() {
        if (dirty == 0) return
        dirty = 0
        prune(words, 4000)
        prune(bigrams, 6000)
        try {
            sp.edit()
                .putString("w", JSONObject(words as Map<*, *>).toString())
                .putString("b", JSONObject(bigrams as Map<*, *>).toString())
                .apply()
        } catch (e: Exception) {
        }
    }

    fun clear() {
        words.clear()
        bigrams.clear()
        dirty = 0
        sp.edit().clear().apply()
    }

    fun count(): Int = words.size

    fun matching(prefix: String, limit: Int): List<String> {
        val p = norm(prefix)
        return words.entries
            .filter { it.key.startsWith(p) && it.key != p }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun followers(prev: String, limit: Int): List<String> {
        val p = norm(prev) + "\t"
        return bigrams.entries
            .filter { it.key.startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key.substring(p.length) }
    }
}

class Suggester(val user: UserDict) {

    private fun isBn(s: String) = s.any { it.code in 0x0980..0x09FF }

    private fun matchCase(src: String, w: String): String {
        if (isBn(w) || src.isEmpty()) return w
        if (src.length >= 2 && src.all { it.isUpperCase() }) return w.uppercase()
        if (src[0].isUpperCase()) return w.replaceFirstChar { it.uppercase() }
        if (w == "i") return "I"
        return w
    }

    /** টাইপ করা অংশ দিয়ে শুরু হওয়া শব্দ (প্রয়োজনে ছোট বানান ভুল ধরে) */
    fun complete(prefixRaw: String, limit: Int = 3): List<String> {
        if (prefixRaw.isEmpty()) return emptyList()
        val bn = isBn(prefixRaw)
        val pref = if (bn) prefixRaw else prefixRaw.lowercase()
        val out = LinkedHashSet<String>()
        for (w in user.matching(pref, limit)) out.add(w)
        if (out.size < limit) {
            val dict = if (bn) Words.bn else Words.en
            for (w in dict) {
                if (w.startsWith(pref) && w != pref) {
                    out.add(w)
                    if (out.size >= limit) break
                }
            }
        }
        if (out.isEmpty() && !bn && pref.length >= 3) {
            for (w in Words.en) {
                if (w[0] == pref[0] && kotlin.math.abs(w.length - pref.length) <= 1 && editDistanceAtMost1(w, pref)) {
                    out.add(w)
                    if (out.size >= limit) break
                }
            }
        }
        return out.take(limit).map { matchCase(prefixRaw, it) }
    }

    /** পরের শব্দের অনুমান */
    fun next(prev: String?, bangla: Boolean, limit: Int = 3): List<String> {
        val out = LinkedHashSet<String>()
        if (prev != null && prev.isNotEmpty()) {
            val key = if (isBn(prev)) prev else prev.lowercase()
            for (w in user.followers(key, limit)) out.add(w)
            val seeds = (if (isBn(prev)) Words.bnNext else Words.enNext)[key]
            if (seeds != null) {
                for (w in seeds) {
                    out.add(w)
                    if (out.size >= limit) break
                }
            }
        }
        if (out.size < limit) {
            val starters = if (bangla) Words.bnStart else Words.enStart
            for (w in starters) {
                out.add(w)
                if (out.size >= limit) break
            }
        }
        return out.take(limit).toList()
    }

    private fun editDistanceAtMost1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < la && j < lb) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                edits++
                if (edits > 1) return false
                if (la > lb) i++ else if (lb > la) j++ else {
                    i++
                    j++
                }
            }
        }
        edits += (la - i) + (lb - j)
        return edits <= 1
    }
}
