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
            text = "3DoF Cinema"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val caption = TextView(this).apply {
            text = "Browser, videos and photos. No account or gamepad needed. " +
                "Camera is optional: enable Real world in VR."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 26)
        }
        val start = Button(this).apply {
            text = "Start VR"
            setOnClickListener { launchWithMediaAccess() }
        }
        val gallery = Button(this).apply {
            text = "Allow photos and videos"
            setOnClickListener { requestMediaAccess() }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(caption, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
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

    private fun launchVr() {
        startActivity(Intent(this, VrHomeActivity::class.java))
    }
}
