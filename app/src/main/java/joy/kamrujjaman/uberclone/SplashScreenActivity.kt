package joy.kamrujjaman.uberclone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.common.internal.service.Common
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import joy.kamrujjaman.uberclone.Common.DRIVER_INFO_REFERENCE
import joy.kamrujjaman.uberclone.Common.currentUser
import joy.kamrujjaman.uberclone.model.DriverInfoModel
import kotlinx.android.synthetic.main.activity_splash_screen.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {


    companion object{
        private val LOGIN_REQUEST_CODE = 7171
    }
    private lateinit var providers : List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth : FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var database: FirebaseDatabase
    private lateinit var driverInfoRef:DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplayScreen()
    }

    override fun onStop() {
        super.onStop()
        if (firebaseAuth != null && listener!= null) firebaseAuth.removeAuthStateListener(listener)
    }

    private fun delaySplayScreen() {
        Completable.timer(3,TimeUnit.SECONDS,AndroidSchedulers.mainThread())
            .subscribe {
                firebaseAuth.addAuthStateListener(listener)
            }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash_screen)

        init()


    }

    private fun init() {
        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(DRIVER_INFO_REFERENCE)

        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth->
            val user = myFirebaseAuth.currentUser
            if (user!=null){
                checkUserFromFirebase()
            }else
                showLoginLayout()
        }

    }

    private fun checkUserFromFirebase() {
        driverInfoRef
            .child(firebaseAuth.currentUser!!.uid)
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity,error.message,Toast.LENGTH_LONG).show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                   if (snapshot.exists()){
                       val model = snapshot.getValue(DriverInfoModel::class.java)
                       gotoHomeActivity(model)
                   }else{
                       registerLayout()
                   }
                }
            })
    }

    private fun gotoHomeActivity(model: DriverInfoModel?) {
        currentUser = model
        startActivity(Intent(this,DriverHomeActivity::class.java))
        finish()

    }

    private fun registerLayout() {
        val bulder = AlertDialog.Builder(this,R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register,null)
        val firstName = itemView.findViewById<View>(R.id.firstNameEDT) as TextInputEditText
        val lastName = itemView.findViewById<View>(R.id.lastNameEDT) as TextInputEditText
        val phoneNumber = itemView.findViewById<View>(R.id.phoneNumberEDT) as TextInputEditText
        val continueBtn = itemView.findViewById<View>(R.id.continueBtn) as Button

        if (firebaseAuth.currentUser!!.phoneNumber!=null &&
            !TextUtils.isDigitsOnly(firebaseAuth.currentUser!!.phoneNumber))
            phoneNumber.setText(firebaseAuth.currentUser!!.phoneNumber)

        bulder.setView(itemView)
        val dialog = bulder.create()
        dialog.show()
        continueBtn.setOnClickListener{
            if (TextUtils.isDigitsOnly(firstName.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please enter your First Name",Toast.LENGTH_LONG).show()
                return@setOnClickListener

            }else if (TextUtils.isDigitsOnly(lastName.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please enter your Last Name",Toast.LENGTH_LONG).show()
                return@setOnClickListener

            }else if (TextUtils.isDigitsOnly(phoneNumber.text.toString())){
                Toast.makeText(this@SplashScreenActivity,"Please enter your Phone Number    ",Toast.LENGTH_LONG).show()
                return@setOnClickListener

            }else{
                val model = DriverInfoModel()
                model.firstName = firstName.text.toString()
                model.lastName = lastName.text.toString()
                model.phoneNumber = phoneNumber.text.toString()
                model.rating = 0.0
                driverInfoRef.child(firebaseAuth.currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener{e ->
                        Toast.makeText(this@SplashScreenActivity,e.message,Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loading.visibility =View.GONE

                    }.addOnSuccessListener {
                        Toast.makeText(this@SplashScreenActivity,"Register Successful!",Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loading.visibility =View.GONE
                        gotoHomeActivity(model)
                    }
            }
        }

    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.loginWithPhone)
            .setGoogleButtonId(R.id.loginWithGoogle)
            .build()
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build()
            ,LOGIN_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode== LOGIN_REQUEST_CODE){
            val response = IdpResponse.fromResultIntent(data)
            if (requestCode== Activity.RESULT_OK){
                val user = firebaseAuth.currentUser
            }else
                Toast.makeText(this@SplashScreenActivity,response!!.error!!.message,Toast.LENGTH_LONG).show()
        }
    }
}