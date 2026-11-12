package com.samrat.cardboardhands

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

/** Identical small status in each eye. Also a guaranteed touch rescue menu on a bare phone. */
internal class CinemaStatusHud(activity:Activity,action:(String)->Unit):LinearLayout(activity) {
    private val labels=ArrayList<TextView>()
    init {
        orientation=HORIZONTAL;gravity=Gravity.TOP
        repeat(2){
            val label=TextView(activity).apply {
                textSize=10f;setTextColor(Color.WHITE);setBackgroundColor(0xb0121b26.toInt())
                gravity=Gravity.CENTER;setPadding(8,5,8,5);maxLines=4
                contentDescription="Cinema takip durumu. Dokunarak kurtarma menüsünü aç."
                setOnClickListener {view ->
                    PopupMenu(activity,view).apply {
                        val entries=listOf("scan" to "Odayı tara / kamerayı aç","lock" to "Burada sabitle / kilidi aç",
                            "rescue" to "Panel ve ekranı önüme getir","input" to "El kontrol yöntemini değiştir",
                            "retry" to "EasyAR takibini yeniden başlat","three" to "3DoF kamera/el moduna dön","details" to "Takip / lisans ayrıntıları")
                        entries.forEachIndexed {i,e->menu.add(0,i,0,e.second)}
                        setOnMenuItemClickListener {item->action(entries[item.itemId].first);true}
                        show()
                    }
                }
            }
            labels+=label;addView(label,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        }
    }
    fun render(status:String,detail:String,tracking:Boolean){
        val text=status+"\n"+detail+"\nDokun: oda / sabitle / paneli getir / ayrıntılar"
        labels.forEach {it.text=text;it.setTextColor(if(tracking) Color.rgb(185,243,215) else Color.rgb(255,220,162))}
    }
}
