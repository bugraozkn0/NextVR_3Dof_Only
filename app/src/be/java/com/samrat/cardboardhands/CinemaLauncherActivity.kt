package com.samrat.cardboardhands

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** A phone-side launch menu before inserting the phone into a Cardboard viewer. */
class CinemaLauncherActivity : Activity() {
    private val galleryRequestCode = 2901
    private var launchAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 40)
            setBackgroundColor(Color.rgb(13, 17, 26))
        }
        val title = TextView(this).apply {
            text = "NextVR Cinema"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val caption = TextView(this).apply {
            text = "Browser, videos and photos. No account or gamepad needed. " +
                "3DoF needs no camera. EasyAR 6DoF uses the camera for tracking and hands."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 26)
        }
        val start = Button(this).apply {
            text = "Start VR"
            setOnClickListener { launchVr() }
        }
        val gallery = Button(this).apply {
            text = "Allow photos and videos"
            setOnClickListener { requestMediaAccess() }
        }
        val tracking = Button(this).apply {
            fun refresh() { text="Tracking: "+Settings.sixDofMode(this@CinemaLauncherActivity).title+" (tap to switch)" }
            refresh()
            setOnClickListener {
                val mode=if(Settings.sixDofMode(this@CinemaLauncherActivity)==Settings.SixDofMode.EASYAR)
                    Settings.SixDofMode.NONE else Settings.SixDofMode.EASYAR
                val reason=SixDofSupport.unavailableReason(this@CinemaLauncherActivity,mode)
                if(reason==null) {Settings.setSixDofMode(this@CinemaLauncherActivity,mode);refresh()}
                else android.widget.Toast.makeText(this@CinemaLauncherActivity,reason,android.widget.Toast.LENGTH_LONG).show()
            }
        }
        val file = Button(this).apply {
            text="Open a single video (no gallery permission)"
            setOnClickListener {
                // https://developer.android.com/training/data-storage/shared/documents-files#open-file
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE);type="video/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                },2902)
            }
        }
        root.addView(file, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(title, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(caption, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(tracking, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(start, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(gallery, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestMediaAccess() {
        requestPermissions(mediaPermissions(), galleryRequestCode)
    }

    private fun launchWithMediaAccess() {
        if (mediaPermissions().any { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            launchAfterPermission = true
            requestMediaAccess()
        } else launchVr()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == galleryRequestCode && launchAfterPermission) {
            launchAfterPermission = false
            launchVr() // Browser remains usable even if the gallery permission is denied.
        }
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==2902 && resultCode==RESULT_OK) data?.data?.let {uri ->
            runCatching {contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            startActivity(Intent(this,VrHomeActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
    private fun launchVr() {
        startActivity(Intent(this, VrHomeActivity::class.java))
    }
}
