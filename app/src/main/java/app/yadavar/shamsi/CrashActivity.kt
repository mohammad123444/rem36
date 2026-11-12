package app.yadavar.shamsi

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.yadavar.shamsi.ui.MainActivity

/** Plain-views error screen (no Compose) so it still works if the main UI is what crashes. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashLog.read(this) ?: "گزارشی ثبت نشده است."
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this)
        title.text = "برنامه با خطا روبه‌رو شد. از این صفحه عکس بگیرید و بفرستید."
        title.textSize = 16f
        title.setPadding(pad, pad, pad, pad / 2)

        val body = TextView(this)
        body.text = report.take(3500)
        body.textSize = 11f
        body.typeface = Typeface.MONOSPACE
        body.layoutDirection = View.LAYOUT_DIRECTION_LTR
        body.setTextIsSelectable(true)
        body.setPadding(pad, 0, pad, pad)

        val scroll = ScrollView(this)
        scroll.addView(body)

        val button = Button(this)
        button.text = "پاک کردن و ادامه"
        button.setOnClickListener {
            CrashLog.clear(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(button)
        setContentView(root)
    }
}
