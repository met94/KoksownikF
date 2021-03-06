/* 
 * Copyright (C) 2017 Alan Bara, alanbarasoft@gmail.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package zls.mutek.koksownikf

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import android.os.SystemClock
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import org.xmlpull.v1.XmlPullParserException

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class MainActivity : AppCompatActivity(), View.OnClickListener {
    /*****
     * getters and setters section
     */
    var updateMap: HashMap<String, Any> = HashMap<String, Any>()

    var updatedDetailsMap: HashMap<String, ArrayList<Date>> = HashMap()

    var updateInProgress = false

    var dirs: ArrayList<String> = arrayListOf()
    var notesMap: HashMap<String, Any> = HashMap()
    var playButtonPressed = false
    var stopButtonPressed = true
    var timeCounted: Long = 0
    var saveLogs = false
    internal var mSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener = onSharedPreferenceChange

    /**********************************
     *
     * On Preference Changed
     *
     */

    private
    val onSharedPreferenceChange: SharedPreferences.OnSharedPreferenceChangeListener
        get() = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == SettingsActivity.KEY_THEME) {
                if (sharedPreferences.getBoolean(key, true)) {
                    MainActivity.activeTheme = Themes.Dark
                } else {
                    MainActivity.activeTheme = Themes.Light
                }
            } else if (key == SettingsActivity.KEY_SAVE_LOGS) {
                saveLogs = sharedPreferences.getBoolean(key, false)
            }
        }

    private object Permissions {
        val READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    }

    internal enum class Themes {
        Dark,
        Light
    }

    class Container(v: Any?) {
        var m: Any? = v
        fun get(): Any? { return m }
        fun set(v: Any?) { m=v }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //initializing settings
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        SettingsActivity.initializeResources(this)
        mSettingsListener = onSharedPreferenceChange
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mSettingsListener)
        mSettingsListener.onSharedPreferenceChanged(
            PreferenceManager.getDefaultSharedPreferences(this),
            SettingsActivity.KEY_THEME
        ) //set activeTheme
        saveLogs = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.KEY_SAVE_LOGS, false)

        if (savedInstanceState != null) {
            activeTheme = savedInstanceState.get(ACTIVE_THEME_BUNDLE) as Themes
        }

        if (activeTheme == Themes.Light) {
            setTheme(R.style.AppThemeLight_NoActionBar)
        } else {
            setTheme(R.style.AppTheme_NoActionBar)
        }

        if (activeTheme == Themes.Dark) {
            ContextCompat.getDrawable(this, R.drawable.ic_save_black_48dp)!!.colorFilter =
                ColorMatrixColorFilter(Utils.NEGATIVE_COLORFILTER)
            ContextCompat.getDrawable(this, R.drawable.ic_add_circle_outline_black_48dp)!!.colorFilter =
                ColorMatrixColorFilter(Utils.NEGATIVE_COLORFILTER)
            ContextCompat.getDrawable(this, R.drawable.ic_history_black_48dp)!!.colorFilter =
                ColorMatrixColorFilter(Utils.NEGATIVE_COLORFILTER)
        } else if (activeTheme == Themes.Light) {
            ContextCompat.getDrawable(this, R.drawable.ic_save_black_48dp)!!.clearColorFilter()
            ContextCompat.getDrawable(this, R.drawable.ic_add_circle_outline_black_48dp)!!.clearColorFilter()
            ContextCompat.getDrawable(this, R.drawable.ic_history_black_48dp)!!.clearColorFilter()
        }

        super.onCreate(savedInstanceState)

        if (!Utils.askPermissions(this, arrayOf(Permissions.READ_PHONE_STATE))) {
            return
        }

        //EULA

        if (!Utils.checkPreferencesSecurityKey(this)) {
            val inputSecurityKeyDialogFragment = InputSecurityKeyDialogFragment()
            inputSecurityKeyDialogFragment.show(fragmentManager, getString(R.string.dialog_key_tag))
        }

        setContentView(R.layout.activity_main)

        if (supportActionBar == null) {
            val myToolbar = findViewById<View>(R.id.actionbar_main) as Toolbar
            setSupportActionBar(myToolbar)
            if (activeTheme == Themes.Light) {
                myToolbar.context.setTheme(R.style.AppThemeLight_NoActionBar)
                myToolbar.setTitleTextColor(-0x22000000)
            }
        }

        val fr = MainListFragment.newInstance("root", null, null)
        supportFragmentManager.beginTransaction().add(R.id.fragment_container, fr).commit()

        timer_play_Button.setOnClickListener(this)
        timer_stop_Button.setOnClickListener(this)

        updateMap["details"] = updateMap["details"] ?: HashMap<String, Any>()
        downloadDirsData(fr)
    }

    private fun downloadDirsData(fr: MainListFragment) {
        // Access a Cloud Firestore instance from your Activity
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users").document("CycxoH93888zgq31fry6")
        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    Log.d(TAG, "DocumentSnapshot data: " + document.data)
                    val dirsRef = docRef.collection("dirs")
                    dirsRef.get()
                        .addOnSuccessListener { result ->
                            for (docDirRef in result) {
                                Log.d(TAG, docDirRef.id + " => " + docDirRef.data)
                                dirs.add(docDirRef.data["path"] as String)
                            }
                            fr.initializeAdapterList(dirs)
                        }
                        .addOnFailureListener { exception ->
                            Log.d(TAG, "Error getting documents: ", exception)
                        }
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
    }

    /**********************************
     *
     * On Request Permission Result
     *
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Utils.REQUEST_CODE_ASK_PERMISSIONS) {
            var i = 0
            while (i < permissions.size && i < grantResults.size) {
                if (permissions[i].compareTo(Permissions.READ_PHONE_STATE) == 0) {
                    if (grantResults[i] == PERMISSION_DENIED) {
                        val dialog = Dialog(this)
                        dialog.setContentView(R.layout.dialog_noperms)
                        dialog.setTitle(R.string.dialog_missing_permsTitle)
                        dialog.setOnDismissListener { finish() }
                        dialog.show()
                    } else {
                        recreate()
                    }
                }
                i++
            }
        }
    }

    public override fun onStop() {
        super.onStop()

        if (saveLogs) {
            Utils.saveLogcatToFile(this)
        }
    }


    override fun onSaveInstanceState(savedInstanceState: Bundle) {

        savedInstanceState.putSerializable(ACTIVE_THEME_BUNDLE, activeTheme)

        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.timer_play_Button -> {
                if (!playButtonPressed) {
                    timer_play_Button.setImageResource(R.drawable.ic_pause_black_48dp)
                    if (stopButtonPressed) {
                        timerChronometer.base = SystemClock.elapsedRealtime()
                    } else {
                        timerChronometer.base = SystemClock.elapsedRealtime() - (timeCounted - timerChronometer.base)
                    }
                    timerChronometer.start()
                    stopButtonPressed = false
                } else {
                    timer_play_Button.setImageResource(R.drawable.ic_play_arrow_black_48dp)
                    timeCounted = SystemClock.elapsedRealtime()
                    timerChronometer.stop()
                }
                playButtonPressed = !playButtonPressed
            }
            R.id.timer_stop_Button -> {
                timerChronometer.base = SystemClock.elapsedRealtime()
                timerChronometer.stop()
                if (playButtonPressed) {
                    timer_play_Button.setImageResource(R.drawable.ic_play_arrow_black_48dp)
                }
                stopButtonPressed = true
                playButtonPressed = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.actionbar_save -> {
                performSave()
                if (saveLogs) {
                    Utils.saveLogcatToFile(this)
                }
                return true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun checkDetailsUpdateFinished(
        newDetailsMap: HashMap<String, Any>,
        cFilteredNewDetailsMap: Container//Map<String, Any>?
    ): Boolean {
        var filteredNewDetailsMap = cFilteredNewDetailsMap.get() as Map<String, Any>?
        if (filteredNewDetailsMap == null) {
            filteredNewDetailsMap = newDetailsMap.filter { mapEntry ->
                updatedDetailsMap[mapEntry.key]?.let {
                    it.size != (mapEntry.value as? Map<*, *>)?.size ?: -1
                } ?: true
            }
            cFilteredNewDetailsMap.set(filteredNewDetailsMap)
        }
        if (filteredNewDetailsMap.isEmpty()) {
            updatedDetailsMap.clear()
            (updateMap["details"] as HashMap<String, HashMap<Date, Any>>).iterator().run {
                while (hasNext()) {
                    next()
                    remove()
                }
            }
            if (!updateInProgress)
                Toast.makeText(this, "No changes detected", Toast.LENGTH_LONG).show()
            updateInProgress = false
            return false
        }

        if (updateInProgress) {
            Toast.makeText(this, "Last update still in progress", Toast.LENGTH_LONG).show()
            return false
        }

        updateInProgress = true
        return true
    }

    fun performSave() {
        if (updateMap.size == 0) return
        val db = FirebaseFirestore.getInstance()
        if (updateMap.size != 0) {
            if (updateMap.containsKey("dirs")) {
                //updateInProgress = true //no need as it should happen immeadietly
                val newDirsMap = updateMap["dirs"] as HashMap<String, Any>
                val collectionRef = db.collection("users").document("CycxoH93888zgq31fry6").collection("dirs")
                for ((key, value) in newDirsMap) {
                    val docRef = collectionRef.document()
                    val newVal = HashMap<String, Any>()
                    newVal["path"] = value
                    docRef.set(newVal)
                }
                updateMap.remove("dirs")
            }

            if (updateMap.containsKey("details")) {
                val newDetailsMap = updateMap["details"] as HashMap<String, Any>
                /*
                val filteredNewDetailsMap = newDetailsMap.filter { mapEntry ->
                    updatedDetailsMap[mapEntry.key]?.let {
                        it.size != (mapEntry.value as? Map<*, *>)?.size ?: -1
                    } ?: true
                } */

                val cFilteredNewDetailsMap = Container(null)

                if (!checkDetailsUpdateFinished(newDetailsMap, cFilteredNewDetailsMap) || cFilteredNewDetailsMap.get() == null)
                    return

                var filteredNewDetailsMap: HashMap<String, Any> = cFilteredNewDetailsMap.get() as HashMap<String, Any>

                val collectionRef = db.collection("users").document("CycxoH93888zgq31fry6").collection("dirs")

                for ((key, value) in filteredNewDetailsMap) {
                    val updateMap = value as HashMap<Date, Any>
                    val query = collectionRef.whereEqualTo("path", key)
                    query.get().addOnSuccessListener {
                        it.forEach {
                            val notesCollectionRef = it.reference.collection("notes")

                            val filteredUpdateMap = updateMap.filter { mapEntry ->
                                updatedDetailsMap[key]?.let {
                                    !it.contains(mapEntry.key)
                                } ?: true
                            }
                            if (filteredUpdateMap.isNotEmpty()) {
                                for ((dateCreated, updateDataMap) in filteredUpdateMap) {
                                        val query = notesCollectionRef.whereEqualTo("created", dateCreated)
                                        query.get().addOnSuccessListener {
                                            val notes = notesMap[key] as? ArrayList<HashMap<String, *>>
                                            val newVal = HashMap<String, Any>()
                                            newVal["created"] = dateCreated
                                            newVal["data"] = updateDataMap
                                            if (it.size() == 0) {
                                                val noteDoc = notesCollectionRef.document()
                                                noteDoc.set(newVal)
                                            } else {
                                                it.forEach {

                                                    (newVal["data"] as? HashMap<String, String>)?.let {
                                                        notes?.filter { note ->
                                                            note["created"] == dateCreated
                                                        }
                                                            ?.let { list ->
                                                                if (list.isEmpty())
                                                                    list.size
                                                                (list.get(0)
                                                                    .get("data") as? HashMap<String, String>)?.let { data ->
                                                                data.forEach { d ->
                                                                    if (!it.containsKey(d.key)) {
                                                                        it.put(d.key, d.value)
                                                                    }
                                                                }
                                                            }
                                                            }
                                                    }
                                                    it.reference.update(newVal).addOnSuccessListener {
                                                        Log.d(TAG, "addOnSuccessListener update reference")
                                                    }.addOnCompleteListener {
                                                        Log.d(TAG, "addOnCompleteListener update reference")
                                                    }
                                                }
                                            }
                                            updatedDetailsMap[key] = updatedDetailsMap[key] ?: ArrayList()
                                            updatedDetailsMap[key]?.add(dateCreated)
                                            checkDetailsUpdateFinished(newDetailsMap, Container(null))
                                        }.addOnFailureListener {
                                            Log.d(TAG, "Error getting documents: ", it)
                                        }
                                }
                            }
                        }
                    }.addOnFailureListener {
                        Log.d(TAG, "Error getting documents: ", it)
                    }
                }
            }
        }
    }

    companion object {

        private val TAG = "MAIN_TAG"

        internal var activeTheme = Themes.Dark
        internal val ACTIVE_THEME_BUNDLE = "activeTheme_BUNDLE"
    }
}
