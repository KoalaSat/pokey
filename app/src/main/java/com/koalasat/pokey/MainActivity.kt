package com.koalasat.pokey

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.databinding.ActivityMainBinding
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.ExternalSigner
import com.koalasat.pokey.models.NostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val requestCodePostNotifications: Int = 1
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        init()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_notifications,
                R.id.navigation_relays,
            ),
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val hiddenNavDestinations = setOf(
            R.id.navigation_configuration,
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in hiddenNavDestinations) {
                navView.visibility = View.GONE
            } else {
                navView.visibility = View.VISIBLE
            }
        }

        handleMuteIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCodePostNotifications,
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleMuteIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Pokey.updateAppHasFocus(true)
    }

    override fun onPause() {
        super.onPause()
        Pokey.updateAppHasFocus(false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePostNotifications) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(applicationContext, getString(R.string.permissions_required), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return when (item.itemId) {
            R.id.action_settings -> {
                navController.navigate(R.id.navigation_configuration)
                true
            }
            R.id.refresh_private_mute -> {
                updateMuteLists()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    fun init() {
        EncryptedStorage.init(this)
        ExternalSigner.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(applicationContext, "common").applicationDao()
            for (user in dao.getSignerUsers()) {
                ExternalSigner.startLauncher(user.hexPub)
            }
        }
    }

    private fun handleMuteIntent(intent: Intent?) {
        intent?.let {
            val muteAction = it.getStringExtra("EXTRA_NOTIFICATION_ACTION")
            when (muteAction) {
                "MUTE" -> {
                    val eventId = it.getStringExtra("eventId")
                    val hexPub = it.getStringExtra("hexPub")

                    if (eventId != null && hexPub != null) {
                        displayMutePopup(hexPub, eventId)
                    }
                }
                "REFRESH" -> {
                    updateMuteLists()
                }
            }
        }
    }

    private fun updateMuteLists() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(applicationContext, "common").applicationDao()
            for (user in dao.getSignerUsers()) {
                NostrClient.fetchMuteList(applicationContext, user.hexPub)
            }
        }
    }

    private fun displayMutePopup(hexPub: String, eventId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext, "common")
            val notification = db.applicationDao().getNotification(hexPub, eventId)

            val text = if ((notification.text?.length ?: 0) > 100) {
                notification.text?.take(100) + "..."
            } else {
                notification.text
            }

            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle(R.string.mute)
                builder.setMessage(text)

                builder.setPositiveButton(R.string.mute_thread) { dialog: DialogInterface, _: Int ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext, "common")
                        val notification = db.applicationDao().getNotification(hexPub, eventId)
                        NostrClient.publishMuteThread(applicationContext, notification)
                        dialog.dismiss()
                    }
                }

                builder.setNegativeButton(R.string.mute_user) { dialog: DialogInterface, _: Int ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext, "common")
                        val notification = db.applicationDao().getNotification(hexPub, eventId)
                        NostrClient.publishMuteUser(applicationContext, notification.accountKexPub)
                        dialog.dismiss()
                    }
                }

                builder.setNeutralButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }

                val dialog = builder.create()
                dialog.show()
            }
        }
    }
}
