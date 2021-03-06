package edu.piedpiper.uw.ischool.watsonchat

import android.content.*
import android.Manifest
import android.net.ConnectivityManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import android.widget.Toast
import com.ibm.watson.developer_cloud.personality_insights.v3.PersonalityInsights
import com.ibm.watson.developer_cloud.personality_insights.v3.model.ProfileOptions
//import com.ibm.watson.developer_cloud.personality_insights.v3.PersonalityInsights
//import com.ibm.watson.developer_cloud.personality_insights.v3.model.ProfileOptions
import java.io.*
import java.util.*


class ProfileActivity : AppCompatActivity() {

    lateinit var mFirebaseAuth: FirebaseAuth
    lateinit var currentUser: FirebaseUser
    var mFirebaseUser: FirebaseUser? = null

    private var connectionReciever: BroadcastReceiver = ConnectivityChangeReceiver()

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectionReciever)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver( connectionReciever, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        registerReceiver( connectionReciever, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1);


        mFirebaseAuth = FirebaseAuth.getInstance();
        val mFirebaseUser = mFirebaseAuth.getCurrentUser();

        val persButton = findViewById(R.id.button2) as Button
        persButton.isEnabled = false
        persButton.text = "Loading..."

        // Preliminary check to ensure login user
        if (mFirebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
            currentUser = mFirebaseUser
        }

        val currentUserKey = currentUser.uid

        val chatUserRef = FirebaseDatabase.getInstance().reference.child("userMessages").child(currentUserKey).child("chat")

        var messageList = ArrayList<String>()

        chatUserRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for(child:DataSnapshot in dataSnapshot.children) {
                    messageList.add(child.value.toString())
                }

                Log.i("ugh", messageList.toString())
                Log.i("bobla", "SPRING QUARTER")

                createFile(messageList)
                persButton.isEnabled = true
                persButton.setText("Analyze")
            }
            override fun onCancelled(p0: DatabaseError?) {}
        })


        persButton.setOnClickListener() {
            if (isOnline(this)) {
                val policy = StrictMode.ThreadPolicy.Builder()
                        .permitAll().build()
                StrictMode.setThreadPolicy(policy)

                var profiler = ""
                val service = PersonalityInsights("2017-10-13")
                service.setUsernameAndPassword("a2a3e22d-e572-4884-9598-ef6e5d334619", "prGs1xnZOB8r")

                try {
                    // Text file implementation (works better than JSON)
                    //  File currently at /sdcard/mytest/txt on my device

                    val root = File("/sdcard/WatsonUserMessage.txt")
                    if (!root.exists()) {
                        root.createNewFile()
                    }

                    val sc: Scanner = Scanner(FileInputStream(root))
                    var count: Int = 0
                    while (sc.hasNext()) {
                        sc.next();
                        count++
                    }
                    sc.close()

                    if (count > 100) {
                        val filer = deviceReader(root)

                        // sets up Profile and executes the call to IBM Watson
                        val options = ProfileOptions.Builder()
                                .text(filer)
                                .rawScores(true).build()
                        val profile = service.profile(options).execute()

                        if (profile.consumptionPreferences != null) {
                            print(profile.consumptionPreferences.size)
                        } else {
                            print("NULL")
                        }

                        val consumption: HashMap<String, Map<String, Double>> = HashMap()
                        val values: HashMap<String, ArrayList<Double>> = HashMap()
                        val needs: HashMap<String, ArrayList<Double>> = HashMap()
                        val personality: HashMap<HashMap<String, ArrayList<Double>>, HashMap<String, ArrayList<Double>>> = HashMap()

                        /**
                         * This processes the data for consumption and puts it into a map for easy access
                         */

                        if (profile.consumptionPreferences != null) {
                            for (a in 0..profile.consumptionPreferences.size - 1) {
                                val category: String = profile.consumptionPreferences[a].consumptionPreferenceCategoryId
                                val items: HashMap<String, Double> = HashMap<String, Double>()

                                for (b in 0..profile.consumptionPreferences[a].consumptionPreferences.size - 1) {
                                    items.put(profile.consumptionPreferences[a].consumptionPreferences[b].name,
                                            profile.consumptionPreferences[a].consumptionPreferences[b].score)
                                }

                                consumption.put(category, items)
                            }
                        }

                        /**
                         * This processes the data for 'values' and puts it into a map with the value name
                         * as the key and raw score & percentile as values
                         */
                        if (profile.values != null) {
                            for (a in 0..profile.values.size - 1) {
                                val value: String = profile.values[a].name
                                val percentile: Double = profile.values[a].percentile
                                val raw: Double = profile.values[a].rawScore
                                values.put(value, arrayListOf(percentile * 100.0, raw * 100.0))
                            }
                        }

                        println("VALUES MAIN" + values)

                        /**
                         * This processes the data for 'needs' and puts it into a map with the value name
                         * as the key and raw score & percentile as values
                         */

                        if (profile.needs != null) {
                            for (a in 0..profile.needs.size - 1) {
                                val need: String = profile.needs[a].name
                                val percentile: Double = profile.needs[a].percentile
                                val raw: Double = profile.needs[a].rawScore

                                needs.put(need, arrayListOf(percentile * 100.0, raw * 100.0))
                            }
                        }

                        /**
                         * This processes the data for the 'personality' category.
                         */
                        if (profile.personality != null) {
                            for (a in 0..profile.personality.size - 1) {
                                val topLevel: HashMap<String, ArrayList<Double>> = HashMap()
                                val temp: HashMap<String, ArrayList<Double>> = HashMap()

                                val p = profile.personality[a].name
                                val child = profile.personality[a].children

                                topLevel.put(p, arrayListOf(profile.personality[a].percentile * 100.0, profile.personality[a].rawScore * 100.0))
                                for (b in 0..child.size - 1) {

                                    val name: String = child[b].name
                                    val percentile: Double = child[b].percentile
                                    val raw: Double = child[b].rawScore

                                    temp.put(name, arrayListOf(percentile * 100.0, raw * 100.0))
                                }
                                personality.put(topLevel, temp)
                            }
                        }

                        // Attaches all the data maps to send with the intent to the overview screen
                        val bundle: Bundle = Bundle()
                        bundle.putSerializable("consumption", consumption)
                        bundle.putSerializable("values", values)
                        bundle.putSerializable("needs", needs)
                        bundle.putSerializable("personality", personality)
                        bundle.putLong("words", profile.wordCount)

                        val intent: Intent = Intent(this, Personality::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)

                        profiler = profile.toString()
                    } else {
                        val dialog = AlertDialog.Builder(this)

                        dialog.setTitle("Not Enough Data")

                        dialog.setMessage("You have sent fewer than 100 words! We need at least 100 words " +
                                "to analyze before we can give you a proper personality assessment. Also note " +
                                "that the more words you send. The more accurate the assessment will be!")


                        dialog.setNeutralButton("Okay") { _, _ ->

                        }


                        val d: AlertDialog = dialog.create()

                        d.show()
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            } else {
                displayAlert()
            }
        }



    }

    private fun displayAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setPositiveButton("Yes", DialogInterface.OnClickListener { dialog, id ->
            startActivityForResult(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS), 0)
        })
        builder.setNegativeButton("No", DialogInterface.OnClickListener { dialog, id ->
            dialog.cancel()
        })
        builder.setMessage("You are not connected to the Internet. Airplane mode may be on, your wifi could be off" +
                " or you may not have service. Would you like to go to settings now to try to fix this?")
                .setTitle("Connectivity Issues")

        val dialog = builder.create()
        dialog.show()
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        //should check null because in airplane mode it will be null
        return netInfo != null && netInfo.isConnected
    }

    fun createFile(sBody: ArrayList<String>) {
        try {
            val root = File("/sdcard/WatsonUserMessage.txt")
            if (!root.exists()) {
                root.createNewFile()
            }
            val output = BufferedWriter(FileWriter(root, false))
            for(i in 0..(sBody.size - 1)) {
                output.append(sBody[i])
                output.newLine()
            }
            output.close()

        } catch (e: IOException) {
            e.printStackTrace()

        }
    }

    // Reads a device from a given file name and stores it as a String to return
    fun deviceReader(name: File): String {
        val bufferedReader: BufferedReader = name.bufferedReader()

        val inputString = bufferedReader.use { it.readText() }

        bufferedReader.close()

        return inputString
    }

}



