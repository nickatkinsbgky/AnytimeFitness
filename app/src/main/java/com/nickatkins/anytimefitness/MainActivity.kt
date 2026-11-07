package com.nickatkins.anytimefitness

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnSignup).setOnClickListener {
            openUrl("https://join.anytimefitness.com/4213/plans?session_id=425a7bd8-e282-431d-810e-86971ffb9b0f&_gl=1*xhx3gn*_gcl_au*MTk4NjkwNjYzNy4xNzkzOTg2NDY2*_ga*MTQxMjk0NjA1MC4xNzc2MjY5MTQ5*_ga_Y37200XWG9*czE3OTM5ODY0NjckbzEkZzEkdDE3OTM5ODY3NTckajQ2JGwwJGgw")
        }

        findViewById<MaterialButton>(R.id.btnClubOS).setOnClickListener {
            openUrl("https://app.club-os.com/action/Dashboard/view#")
        }

        findViewById<MaterialButton>(R.id.btnAbc).setOnClickListener {
            openUrl("https://client.abcfinancial.com/(S(art2cnqfw0mvlx45ulmtzybg))/default.aspx?_gl=1*15bvxrc*_gcl_au*OTExNDcwMTYwLjE3OTMwMzkyMzI.")
        }

        // New: Club OS credentials button
        findViewById<MaterialButton>(R.id.btnClubOsSettings).setOnClickListener {
            showCredentialsDialog()
        }

        findViewById<MaterialButton>(R.id.btnRefreshWidget).setOnClickListener {
            refreshWidget()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCredentialsDialog() {
        val (savedUser, savedPass) = CredentialStore.load(this)

        val layout = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val userEdit = layout.findViewById<EditText>(R.id.editUsername)
        val passEdit = layout.findViewById<EditText>(R.id.editPassword)
        userEdit.setText(savedUser ?: "")
        passEdit.setText(savedPass ?: "")

        AlertDialog.Builder(this)
            .setTitle("Club OS Credentials")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val user = userEdit.text.toString().trim()
                val pass = passEdit.text.toString()
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    CredentialStore.save(this, user, pass)
                    Toast.makeText(this, "Saved. Refresh the widget.", Toast.LENGTH_LONG).show()
                    refreshWidget()
                } else {
                    Toast.makeText(this, "Both fields required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            ComponentName(this, WhosInClubWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            WhosInClubWidgetProvider.updateAppWidget(this, manager, ids[0])
            Toast.makeText(this, "Widget refreshing…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Add the widget to your home screen first", Toast.LENGTH_LONG).show()
        }
    }
}
