package com.glasskb.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** পেছনের নরম রঙিন আলো — গ্লাস ইফেক্টের জন্য */
class BlobBg(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        blob(c, w * 0.12f, h * 0.10f, w * 0.60f, 0x667F5AF0)
        blob(c, w * 0.98f, h * 0.45f, w * 0.65f, 0x552CB1BC)
        blob(c, w * 0.15f, h * 0.92f, w * 0.60f, 0x55FF7EB3)
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        p.shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
    }
}

/** থিমের ছোট প্রিভিউ — আসল কীবোর্ডের মতো দেখায় */
class ThemePreview(ctx: Context, private val t: KbTheme) : View(ctx) {
    private val bgP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        bgP.shader = LinearGradient(0f, 0f, w, h, t.bg, null, Shader.TileMode.CLAMP)
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, 14f * d, 14f * d, bgP)

        val pad = 8f * d
        val gap = 3f * d
        // সাজেশন বার
        val sh = h * 0.13f
        keyP.shader = null
        keyP.color = t.keyTop
        for (i in 0..2) {
            val pw = (w - pad * 2 - gap * 2) / 3f
            rect.set(pad + i * (pw + gap), pad, pad + i * (pw + gap) + pw, pad + sh)
            c.drawRoundRect(rect, 4f * d, 4f * d, keyP)
        }
        // কী সারি
        val rows = intArrayOf(7, 6, 7)
        val top0 = pad + sh + gap * 1.5f
        val kh = (h - top0 - pad - gap * 3f) / 4f
        for (r in 0..2) {
            val cols = rows[r]
            val offset = (7 - cols) * 0.5f
            val kw = (w - pad * 2 - gap * 6) / 7f
            for (i in 0 until cols) {
                val x = pad + (i + offset) * (kw + gap)
                val y = top0 + r * (kh + gap)
                rect.set(x, y, x + kw, y + kh)
                keyP.shader = LinearGradient(0f, y, 0f, y + kh, t.keyTop, t.keyBottom, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, 4f * d, 4f * d, keyP)
                strokeP.color = t.keyStroke
                c.drawRoundRect(rect, 4f * d, 4f * d, strokeP)
            }
        }
        // স্পেস ও এন্টার
        val y = top0 + 3 * (kh + gap)
        val sw = (w - pad * 2) * 0.72f
        rect.set(pad, y, pad + sw, y + kh)
        keyP.shader = LinearGradient(0f, y, 0f, y + kh, t.keyTop, t.keyBottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 4f * d, 4f * d, keyP)
        strokeP.color = t.keyStroke
        c.drawRoundRect(rect, 4f * d, 4f * d, strokeP)
        rect.set(pad + sw + gap, y, w - pad, y + kh)
        keyP.shader = LinearGradient(0f, y, w, y, t.accentStart, t.accentEnd, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 4f * d, 4f * d, keyP)
    }
}

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var clips: ClipStore
    private var enabledTv: TextView? = null
    private var selectedTv: TextView? = null
    private var clipCountTv: TextView? = null
    private var learnedTv: TextView? = null

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val INK = Color.parseColor("#FF1F2547")
    private val SUB = Color.parseColor("#FF5C6490")
    private val ACC1 = Color.parseColor("#FF7F5AF0")
    private val ACC2 = Color.parseColor("#FF2CB1BC")

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun tv(s: String, size: Float, bold: Boolean = false, color: Int = INK): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun glass(radius: Float, fill: String = "#B3FFFFFF", stroke: String = "#FFFFFFFF"): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(Color.parseColor(fill))
            setStroke(dp(1.2f), Color.parseColor(stroke))
        }

    private fun accentBg(radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACC1, ACC2)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun card(parent: LinearLayout, title: String = "", sub: String = ""): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            background = glass(20f)
            elevation = dp(2f).toFloat()
        }
        parent.addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12f) })
        if (title.isNotEmpty()) c.addView(tv(title, 16f, true))
        if (sub.isNotEmpty()) c.addView(tv(sub, 12.5f, false, SUB).apply { setPadding(0, dp(2f), 0, dp(4f)) })
        return c
    }

    private fun primaryBtn(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = accentBg(14f)
            setOnClickListener { onClick() }
        }

    private fun softBtn(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(INK)
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = glass(14f, "#99FFFFFF", "#33FFFFFF")
            setOnClickListener { onClick() }
        }

    private fun btnLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) }

    private fun toggleRow(parent: LinearLayout, title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 14.5f, true))
        if (desc.isNotEmpty()) col.addView(tv(desc, 12f, false, SUB))
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        row.addView(sw, LinearLayout.LayoutParams(WRAP, WRAP))
        parent.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** ট্যাপ করলে খোলে/বন্ধ হয় এমন কার্ড */
    private fun expandCard(parent: LinearLayout, title: String, body: String) {
        val c = card(parent)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(tv(title, 15.5f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val arrow = tv("﹀", 15f, true, SUB)
        head.addView(arrow)
        c.addView(head)
        val b = tv(body, 13.5f, false, SUB).apply {
            setPadding(0, dp(8f), 0, 0)
            setLineSpacing(0f, 1.15f)
            visibility = View.GONE
        }
        c.addView(b)
        c.setOnClickListener {
            val open = b.visibility == View.VISIBLE
            b.visibility = if (open) View.GONE else View.VISIBLE
            arrow.text = if (open) "﹀" else "︿"
        }
    }

    private fun scrollPage(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(4f), dp(16f), dp(20f))
        }
        sv.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        return Pair(sv, col)
    }

    // ------------------------------------------------------------------ পেজ: হোম

    private fun buildHome(col: LinearLayout) {
        val hero = card(col)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.setPadding(dp(16f), dp(22f), dp(16f), dp(22f))
        hero.addView(TextView(this).apply {
            text = "⌨️"
            textSize = 40f
            gravity = Gravity.CENTER
            background = accentBg(26f)
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        }, LinearLayout.LayoutParams(WRAP, WRAP))
        hero.addView(tv("গ্লাস কীবোর্ড", 26f, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
        })
        hero.addView(tv("অভ্র  •  প্রভাত / জাতীয়  •  English", 14f, false, SUB).apply { gravity = Gravity.CENTER })

        val setup = card(col, "🚀 শুরু করুন", "দুটো ধাপে কীবোর্ড চালু করুন")
        enabledTv = tv("", 14f).apply { setPadding(0, dp(6f), 0, 0) }
        selectedTv = tv("", 14f).apply { setPadding(0, dp(2f), 0, 0) }
        setup.addView(enabledTv)
        setup.addView(selectedTv)
        setup.addView(primaryBtn("১. কীবোর্ড চালু করুন") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, btnLp())
        setup.addView(primaryBtn("২. এই কীবোর্ড বেছে নিন") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, btnLp())

        val test = card(col, "✍️ টাইপ করে দেখুন")
        val et = EditText(this).apply {
            hint = "এখানে ট্যাপ করে লিখুন…"
            setHintTextColor(Color.parseColor("#995C6490"))
            setTextColor(INK)
            minLines = 3
            gravity = Gravity.TOP
            background = glass(14f, "#99FFFFFF", "#22000000")
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        }
        test.addView(et, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })

        val tip = card(col, "💡 টিপ")
        tip.addView(tv(
            "স্পেসবার ডানে বা বামে স্লাইড করলে English → অভ্র → প্রভাত (বা জাতীয়) ঘুরে ঘুরে বদলায়।",
            13.5f, false, SUB
        ).apply { setPadding(0, dp(4f), 0, 0) })
    }

    // ------------------------------------------------------------------ পেজ: থিম

    private fun buildThemes(col: LinearLayout) {
        col.addView(tv("🎨 থিম বেছে নিন", 20f, true).apply { setPadding(dp(2f), dp(6f), 0, dp(2f)) })
        col.addView(tv("ট্যাপ করলেই কীবোর্ডে সাথে সাথে বদলে যাবে", 12.5f, false, SUB).apply {
            setPadding(dp(2f), 0, 0, dp(4f))
        })
        val cards = ArrayList<LinearLayout>()
        fun refresh() {
            cards.forEachIndexed { i, c ->
                val sel = i == prefs.theme
                c.background = if (sel) {
                    GradientDrawable().apply {
                        cornerRadius = dp(18f).toFloat()
                        setColor(Color.parseColor("#D9FFFFFF"))
                        setStroke(dp(2.5f), ACC1)
                    }
                } else glass(18f)
            }
        }
        var rowLayout: LinearLayout? = null
        Themes.all.forEachIndexed { i, t ->
            if (i % 2 == 0) {
                rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(rowLayout, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10f) })
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
                setOnClickListener {
                    prefs.theme = i
                    refresh()
                }
            }
            item.addView(ThemePreview(this, t), LinearLayout.LayoutParams(MATCH, dp(96f)))
            item.addView(tv(t.name, 13.5f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, dp(2f))
            })
            cards.add(item)
            rowLayout?.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                setMargins(if (i % 2 == 0) 0 else dp(5f), 0, if (i % 2 == 0) dp(5f) else 0, 0)
            })
        }
        if (Themes.all.size % 2 == 1) {
            rowLayout?.addView(View(this), LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        refresh()
    }

    // ------------------------------------------------------------------ পেজ: অপশন

    private fun buildOptions(col: LinearLayout) {
        col.addView(tv("⚙️ অপশন", 20f, true).apply { setPadding(dp(2f), dp(6f), 0, dp(6f)) })

        val groups = ArrayList<LinearLayout>()
        val segLabels = listOf("⌨️ টাইপিং", "🎛️ কন্ট্রোল", "🗂️ ডেটা")
        val segViews = ArrayList<TextView>()
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glass(16f, "#80FFFFFF", "#33FFFFFF")
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        }
        fun select(n: Int) {
            groups.forEachIndexed { i, g -> g.visibility = if (i == n) View.VISIBLE else View.GONE }
            segViews.forEachIndexed { i, v ->
                if (i == n) {
                    v.background = accentBg(12f)
                    v.setTextColor(Color.WHITE)
                } else {
                    v.background = null
                    v.setTextColor(INK)
                }
            }
        }
        segLabels.forEachIndexed { i, l ->
            val v = tv(l, 13.5f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10f), 0, dp(10f))
                setOnClickListener { select(i) }
            }
            segViews.add(v)
            seg.addView(v, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        col.addView(seg, LinearLayout.LayoutParams(MATCH, WRAP))

        // --- টাইপিং
        val g1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val c1 = card(g1, "✨ স্মার্ট টাইপিং")
        toggleRow(c1, "শব্দ সাজেশন", "টাইপ করা শব্দ ও পরের শব্দের অনুমান", prefs.suggest) { prefs.suggest = it }
        toggleRow(c1, "আমার টাইপ থেকে শেখা", "নতুন শব্দ শুধু এই ফোনেই জমা থাকে", prefs.learn) { prefs.learn = it }
        val cl = card(g1, "🅱️ ফিক্সড বাংলা লেআউট", "স্লাইড করলে English ও অভ্রর পাশে এই দুটির মধ্যে শুধু যেটি চালু থাকবে সেটি আসবে")
        val kindBtns = ArrayList<TextView>()
        fun refreshKind() {
            kindBtns.forEachIndexed { i, b ->
                val sel = prefs.fixedKind == i + 1
                b.background = if (sel) accentBg(14f) else glass(14f, "#99FFFFFF", "#33FFFFFF")
                b.setTextColor(if (sel) Color.WHITE else INK)
            }
        }
        val kindRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("প্রভাত", "জাতীয়").forEachIndexed { i, name ->
            val b = tv(name, 15f, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, dp(12f))
                setOnClickListener {
                    prefs.fixedKind = i + 1
                    refreshKind()
                }
            }
            kindBtns.add(b)
            kindRow.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                setMargins(if (i == 0) 0 else dp(4f), 0, if (i == 0) dp(4f) else 0, 0)
            })
        }
        cl.addView(kindRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })
        cl.addView(tv("প্রভাত = ওপরের সারিতে ১২টি কী, শিফটে দ্বিতীয় অক্ষর\nজাতীয় = বর্ণমালার ক্রমে সাজানো (ক খ গ…, স্বর ও কার আলাদা পেজে)", 12f, false, SUB).apply {
            setPadding(0, dp(8f), 0, 0)
        })
        refreshKind()
        val c1b = card(g1, "🔢 নাম্বার")
        toggleRow(c1b, "বাংলা মোডে বাংলা সংখ্যা", "123 এর বদলে ১২৩ বসবে", prefs.bnDigits) { prefs.bnDigits = it }
        groups.add(g1)

        // --- কন্ট্রোল
        val g2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val c2 = card(g2, "🎛️ আচরণ")
        toggleRow(c2, "ভলিউম বাটন = কার্সর", "− চাপলে কার্সর পিছনে, + চাপলে সামনে", prefs.volCursor) { prefs.volCursor = it }
        toggleRow(c2, "কী পপ-আপ", "কী চাপলে বড় করে অক্ষর দেখায়", prefs.popup) { prefs.popup = it }
        toggleRow(c2, "ভাইব্রেশন", "", prefs.vibrate) { prefs.vibrate = it }
        toggleRow(c2, "কী-এর শব্দ", "", prefs.sound) { prefs.sound = it }
        val c3 = card(g2, "📏 কীবোর্ডের সাইজ")
        val sizeLabel = tv("", 13.5f, false, SUB)
        fun setLabel(scale: Float) {
            sizeLabel.text = "বর্তমান: ${Math.round(scale * 100f)}%  (ছোট ← → বড়)"
        }
        setLabel(prefs.scale)
        c3.addView(sizeLabel)
        val sb = SeekBar(this).apply {
            max = 70
            progress = Math.round((prefs.scale - 0.7f) * 100f).coerceIn(0, 70)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    setLabel(0.7f + p / 100f)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {
                    val p = s?.progress ?: return
                    prefs.scale = 0.7f + p / 100f
                }
            })
        }
        c3.addView(sb, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })
        val c4 = card(g2, "🎙️ ভয়েস টাইপিং", "🎤 বাটন চেপে বললেই লেখা হবে")
        c4.addView(primaryBtn("মাইক্রোফোনের অনুমতি দিন") {
            startActivity(Intent(this, VoicePermissionActivity::class.java))
        }, btnLp())
        groups.add(g2)

        // --- ডেটা
        val g3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val c5 = card(g3, "📋 ক্লিপবোর্ড ইতিহাস")
        clipCountTv = tv("", 13.5f, false, SUB).apply { setPadding(0, dp(4f), 0, 0) }
        c5.addView(clipCountTv)
        c5.addView(softBtn("ইতিহাস মুছে ফেলুন") {
            clips.clear()
            updateCounts()
            Toast.makeText(this, "ক্লিপবোর্ড ইতিহাস মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
        }, btnLp())
        val c6 = card(g3, "🧠 শেখা শব্দ")
        learnedTv = tv("", 13.5f, false, SUB).apply { setPadding(0, dp(4f), 0, 0) }
        c6.addView(learnedTv)
        c6.addView(softBtn("শেখা শব্দ মুছে ফেলুন") {
            UserDict(this).clear()
            updateCounts()
            Toast.makeText(this, "শেখা শব্দ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
        }, btnLp())
        groups.add(g3)

        for (g in groups) col.addView(g, LinearLayout.LayoutParams(MATCH, WRAP))
        select(0)
    }

    // ------------------------------------------------------------------ পেজ: গাইড

    private fun buildGuide(col: LinearLayout) {
        col.addView(tv("📖 ব্যবহার গাইড", 20f, true).apply { setPadding(dp(2f), dp(6f), 0, dp(2f)) })
        col.addView(tv("ট্যাপ করলে বিস্তারিত দেখা যাবে", 12.5f, false, SUB).apply { setPadding(dp(2f), 0, 0, dp(2f)) })
        expandCard(col, "↔️ ভাষা বদলানো",
            "স্পেসবার ডানে বা বামে স্লাইড করুন।\nEnglish → বাংলা (অভ্র) → বাংলা (প্রভাত অথবা জাতীয়) → English — এভাবে ঘুরে ঘুরে বদলায়। প্রভাত ও জাতীয়ের মধ্যে কোনটি আসবে তা অপশন → টাইপিং থেকে বেছে নিন; দুটো একসাথে আসে না।")
        expandCard(col, "🔠 অভ্র ফোনেটিক নিয়ম",
            "• ami → আমি, tumi → তুমি, bangla → বাংলা\n" +
                "• o বসালে কোনো কার হয় না: kotha → কথা। ‘ো’ লিখতে বড় O: valO → ভালো\n" +
                "• t ত, T ট · d দ, D ড · n ন, N ণ · s স, S শ, Sh ষ · r র, R ড়\n" +
                "• ng → ং, Ng → ঙ, H → ঃ, ^ → ঁ, t` → ৎ\n" +
                "• যুক্তাক্ষর নিজে নিজেই হয়: kt → ক্ত, ggo → জ্ঞ\n" +
                "• যুক্ত ভাঙতে ` দিন: k`t → কত\n" +
                "• . চাপলে ‘।’ হয়, চেপে ধরলে ইংরেজি ডট")
        expandCard(col, "🅿️ প্রভাত লেআউট",
            "ওপরের সারিতে ১২টি কী (দ ূ ী র ট এ ু ি ও প ে ো)। প্রতিটি কী-এর ডানে ওপরে ছোট করে দ্বিতীয় অক্ষর দেখা যায় (ধ ঊ ঈ ড় ঠ…)। শিফট ⇧ চাপলে সেগুলো বসে, অথবা কী চেপে ধরলে সরাসরি বসে। স্পেসবারে ◀ ▶ তীর দেখে বুঝবেন স্লাইড করা যায়।")
        expandCard(col, "🇧🇩 জাতীয় লেআউট",
            "বর্ণমালার ক্রমে সাজানো। এক পেজে ব্যঞ্জনবর্ণ, “অ আ” চাপলে স্বরবর্ণ ও কার। কী চেপে ধরলে যুক্তাক্ষর (ক → ক্ষ, ক্ত…) আসে; ং চেপে ধরলে ঃ।")
        expandCard(col, "👆 লং-প্রেস (চেপে ধরা)",
            "• a s d f g h j k l z → @ # & * - ! ? ( ) \"\n• q w e r t y u i o p → ১ থেকে ০\n• b n m → ' : /\n• x কাট ✂️, c কপি 📋, v পেস্ট 📥 — ১ সেকেন্ড পপ-আপে লেখাটা দেখায়\n• সিম্বল কী চেপে ধরলে আরও চিহ্ন (₹ € £ ≠ ± ÷ ×)")
        expandCard(col, "🔢 নাম্বার প্যাড ও সিম্বল",
            "কোনো নাম্বার / ফোন / তারিখের ঘরে ক্লিক করলে নিজে থেকেই নাম্বার প্যাড খুলে যায় — সেখানে শুধু নাম্বার (আর ঘরের জন্য দরকারি দু-একটা চিহ্ন, যেমন দশমিক . বা ফোনের +) থাকে। সাধারণ লেখার ঘরে টুলবারের 🔢 চাপলে নাম্বার ও + − * / দেখায়। ?123 চাপলে নাম্বার সারি ও সব সিম্বল (দ্বিতীয় পেজে আরও)।")
        expandCard(col, "😊 ইমোজি",
            "ফোন যত ইমোজি দেখাতে পারে সবই আছে। সার্চ বারে চেপে অক্ষর টাইপ করে খুঁজুন। ডানে-বামে স্লাইড করলে পরের বিভাগ। হাতের ইমোজি চেপে ধরলে স্কিন টোন। ♡ ★ ✿ ➤ ধরনের বিশেষ চিহ্ন আলাদা ট্যাবে।")
        expandCard(col, "🎤 ভয়েস ও 🔊 ভলিউম কার্সর",
            "টুলবারের 🎤 চেপে বলুন, লেখা বসে যাবে (ইন্টারনেট ও Google অ্যাপ লাগে)। অপশন থেকে চালু করলে ভলিউম − / + বাটনে কার্সর পিছনে/সামনে যায়।")
    }

    // ------------------------------------------------------------------ পেজ: ডেভেলপার

    private fun infoRow(parent: LinearLayout, icon: String, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(8f))
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            background = glass(14f, "#99FFFFFF", "#33FFFFFF")
        }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
        }
        col.addView(tv(label, 12f, false, SUB))
        col.addView(tv(value, 15f, true))
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        parent.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun buildDeveloper(col: LinearLayout) {
        col.addView(tv("👨‍💻 Developer", 20f, true).apply { setPadding(dp(2f), dp(6f), 0, dp(2f)) })
        val me = card(col)
        me.gravity = Gravity.CENTER_HORIZONTAL
        me.setPadding(dp(16f), dp(22f), dp(16f), dp(16f))
        me.addView(TextView(this).apply {
            text = "S"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC1, ACC2)).apply {
                shape = GradientDrawable.OVAL
            }
        }, LinearLayout.LayoutParams(dp(84f), dp(84f)))
        me.addView(tv("S Om Yogeshananda", 21f, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
        })
        me.addView(tv("B.Sc in CSE  •  Teesta University, Rangpur 🎓", 13.5f, false, SUB).apply { gravity = Gravity.CENTER })

        val info = card(col, "📇 Profile")
        infoRow(info, "👤", "Name", "S Om Yogeshananda")
        infoRow(info, "📘", "Degree", "B.Sc in CSE")
        infoRow(info, "🏫", "University", "Teesta University, Rangpur")

        val tech = card(col, "🛠️ Built With")
        tech.addView(tv(
            "• 🟣 Kotlin & Android SDK (InputMethodService)\n" +
                "• 🧱 Gradle & GitHub Actions (auto build)\n" +
                "• 🎙️ Android SpeechRecognizer (voice typing)\n" +
                "• 😊 Android ICU (emoji names & search)\n" +
                "• 🤖 Designed & coded with AI assistance",
            13.5f, false, SUB
        ).apply {
            setPadding(0, dp(6f), 0, 0)
            setLineSpacing(0f, 1.2f)
        })

        val about = card(col, "💙 About")
        about.addView(tv(
            "Glass Keyboard — an effort to make Bangla & English typing easy, beautiful and fast.\nVersion 2.2 ✨",
            13.5f, false, SUB
        ).apply { setPadding(0, dp(4f), 0, 0) })
    }

    // ------------------------------------------------------------------ মূল কাঠামো

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        clips = ClipStore(this)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FFEAF0FF"), Color.parseColor("#FFF7EBFA"), Color.parseColor("#FFE3F6F4"))
            )
        }
        root.addView(BlobBg(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))

        // হেডার
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(8f))
        }
        header.addView(tv("গ্লাস কীবোর্ড", 22f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(tv("✨", 22f))
        main.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        // পেজ
        val content = FrameLayout(this)
        main.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val pages = ArrayList<ScrollView>()
        val builders = listOf<(LinearLayout) -> Unit>(
            { buildHome(it) }, { buildThemes(it) }, { buildOptions(it) }, { buildGuide(it) }, { buildDeveloper(it) }
        )
        for (b in builders) {
            val (sv, col) = scrollPage()
            b(col)
            content.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
            pages.add(sv)
        }

        // নিচের নেভিগেশন (গ্লাস বার)
        val navIcons = listOf("🏠", "🎨", "⚙️", "📖", "👨‍💻")
        val navNames = listOf("হোম", "থিম", "অপশন", "গাইড", "Developer")
        val items = ArrayList<LinearLayout>()
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glass(26f, "#E6FFFFFF", "#FFFFFFFF")
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            elevation = dp(6f).toFloat()
        }
        fun show(n: Int) {
            pages.forEachIndexed { i, p -> p.visibility = if (i == n) View.VISIBLE else View.GONE }
            items.forEachIndexed { i, it ->
                val sel = i == n
                it.background = if (sel) accentBg(20f) else null
                (it.getChildAt(0) as TextView).alpha = if (sel) 1f else 0.75f
                (it.getChildAt(1) as TextView).setTextColor(if (sel) Color.WHITE else SUB)
            }
            if (n == 2) updateCounts()
        }
        for (i in 0..4) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(7f), 0, dp(7f))
                setOnClickListener { show(i) }
            }
            item.addView(tv(navIcons[i], 19f).apply { gravity = Gravity.CENTER })
            item.addView(tv(navNames[i], 10.5f, true, SUB).apply { gravity = Gravity.CENTER })
            items.add(item)
            nav.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        main.addView(nav, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(14f), dp(4f), dp(14f), dp(12f))
        })
        show(0)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateCounts()
    }

    private fun updateCounts() {
        clipCountTv?.text = "জমা আছে: ${clips.all().size} টি"
        learnedTv?.text = "শেখা শব্দ: ${UserDict(this).count()} টি"
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = cur.startsWith("$packageName/")
        enabledTv?.text = if (enabled) "✅ কীবোর্ড চালু আছে" else "⚠️ কীবোর্ড এখনো চালু করা হয়নি"
        selectedTv?.text = if (selected) "✅ এটাই এখন আপনার কীবোর্ড" else "⚠️ এটা এখনো ডিফল্ট কীবোর্ড নয়"
    }
}
