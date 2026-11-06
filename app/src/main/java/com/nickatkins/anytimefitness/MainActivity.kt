package com.nickatkins.anytimefitness

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnWaiver).setOnClickListener {
            openUrl("https://f.cosdgw.com/dgw/Mjg5MjMxNjkz")
        }

        findViewById<MaterialButton>(R.id.btnSignup).setOnClickListener {
            openUrl("https://join.anytimefitness.com/4213/plans?session_id=425a7bd8-e282-431d-810e-86971ffb9b0f&_gl=1*xhx3gn*_gcl_au*MTk4NjkwNjYzNy4xNzkzOTg2NDY2*_ga*MTQxMjk0NjA1MC4xNzc2MjY5MTQ5*_ga_Y37200XWG9*czE3OTM5ODY0NjckbzEkZzEkdDE3OTM5ODY3NTckajQ2JGwwJGgw")
        }

        findViewById<MaterialButton>(R.id.btnClubOS).setOnClickListener {
            openUrl("https://app.club-os.com/action/Dashboard/view#")
        }

        findViewById<MaterialButton>(R.id.btnAbc).setOnClickListener {
            openUrl("https://client.abcfinancial.com/(S(art2cnqfw0mvlx45ulmtzybg))/default.aspx?_gl=1*15bvxrc*_gcl_au*OTExNDcwMTYwLjE3OTMwMzkyMzI.")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }
}
