package com.example.rekognitiontest

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.inflate
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ColorStateListInflaterCompat.inflate
import androidx.core.graphics.drawable.DrawableCompat.inflate
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amazonaws.regions.Regions
import com.amazonaws.services.rekognition.model.*
import com.amazonaws.services.s3.model.Region
import com.amazonaws.util.IOUtils
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.Amplify
import com.amplifyframework.core.AmplifyConfiguration
import com.amplifyframework.core.Consumer
import com.amplifyframework.core.category.CategoryConfiguration
import com.amplifyframework.predictions.PredictionsException
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin
import com.amplifyframework.predictions.models.IdentifyActionType
import com.amplifyframework.predictions.models.LanguageType
import com.amplifyframework.predictions.options.IdentifyOptions
import com.amplifyframework.predictions.result.IdentifyEntityMatchesResult
import com.amplifyframework.predictions.result.IdentifyResult
import com.amplifyframework.storage.StorageAccessLevel
import com.amplifyframework.storage.options.StorageListOptions
import com.amplifyframework.storage.options.StorageUploadFileOptions
import kotlinx.android.synthetic.main.name_dialog.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.*
import java.net.URI
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var imageView: ImageView
    lateinit var captureButton: Button
    lateinit var rekogniseButton: Button
    lateinit var nameText: TextView
    lateinit var progress: ProgressBar
    lateinit var viewMatchesButton: Button

    val REQUEST_IMAGE_CAPTURE = 1
    private val PERMISSION_REQUEST_CODE: Int = 101
    private var mCurrentPhotoPath: String? = null
    private var compressedPhotoFile: File? = null
    private var matches = ArrayList<FaceMatch>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageView = findViewById(R.id.imageView)
        captureButton = findViewById(R.id.button6)
        captureButton.setOnClickListener(View.OnClickListener {
            nameText.text = ""
            viewMatchesButton.visibility = View.INVISIBLE
            if (checkPermission()) takePicture() else requestPermission()
        })
        rekogniseButton = findViewById(R.id.button7)
        rekogniseButton.setOnClickListener(View.OnClickListener {
            nameText.text = ""
            CoroutineScope(IO).launch {
                if(compressedPhotoFile !== null) {
                    runOnUiThread{progress.visibility = View.VISIBLE}
                    checkImageAgainstFaces(compressedPhotoFile!!)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Please take a picture first", Toast.LENGTH_SHORT).show()
                    }
                }

            }
        })
        nameText = findViewById(R.id.textView)
        progress = findViewById(R.id.progressBar)
        viewMatchesButton = findViewById(R.id.viewMatchesButton)
        viewMatchesButton.setOnClickListener {
            val intent: Intent = Intent(this@MainActivity, Matches()::class.java)
            intent.putExtra("matches", matches)
            startActivity(intent)
        }


        signIn()
//        uploadFile()
//        signUp()
//        confirmSignUp("062465")
    }

    private fun signUp(){
        Amplify.Auth.signUp(
            "+11234567890",
            "Password123",
            AuthSignUpOptions.builder().userAttribute(
                AuthUserAttributeKey.email(),
                "danedelling1@mailinator.com"
            ).build(),
            { result -> Log.i("AuthQuickStart", "Result: $result") },
            { err -> Log.e("AuthQuickstart", "Sign up failed", err) }
        )
    }

    private fun confirmSignUp(confirmCode: String){
        Amplify.Auth.confirmSignUp(
            "+16176207653",
            confirmCode,
            { result ->
                Log.i(
                    "AuthQuickstart",
                    if (result.isSignUpComplete) "Confirm signUp succeeded" else "Confirm sign up not complete"
                )
            },
            { error -> Log.e("AuthQuickstart", error.toString()) }
        )
    }

    private fun signIn(){
        Amplify.Auth.signIn(
            "+11234567890",
            "Password123",
            { result ->
                Log.i(
                    "AuthQuickstart",
                    if (result.isSignInComplete) "Sign in succeeded" else "Sign in not complete"
                )
            },
            { error -> Log.e("AuthQuickstart", error.toString()) }
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
       when(requestCode){
           PERMISSION_REQUEST_CODE -> {
               if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                   takePicture()
               } else {
                   Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
               }
               return
           }
           else -> {

           }
       }
    }

    private fun takePicture(){
        val intent: Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val file: File = createFile()
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "com.example.rekognitiontest.fileProvider",
            file
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun compressImage(file: File): File{
        val o: BitmapFactory.Options = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        o.inSampleSize = 6
        var inputStream = FileInputStream(file)
        BitmapFactory.decodeStream(inputStream, null, o)
        inputStream.close()
        val requiredSize = 75

        var scale = 1
        while(o.outWidth / scale / 2 >= requiredSize && o.outHeight / scale / 2 >= requiredSize){
            scale *= 2
        }

        val o2 = BitmapFactory.Options()
        o2.inSampleSize = scale
        inputStream = FileInputStream(file)
        val selectedBitmap = BitmapFactory.decodeStream(inputStream, null, o2)
        inputStream.close()

        val newFile = createFile()
        val outputStream = FileOutputStream(newFile)
        selectedBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

        file.delete()
        return newFile
    }

    private suspend fun checkImageAgainstFaces(imgFile: File){
        try{
            val predictionsPlugin = Amplify.Predictions.getPlugin("awsPredictionsPlugin") as AWSPredictionsPlugin
            val escapeHatch = predictionsPlugin.escapeHatch
            val client = escapeHatch.rekognitionClient
            client.setRegion(com.amazonaws.regions.Region.getRegion(Regions.AP_SOUTHEAST_1))

            val inputStream: InputStream = FileInputStream(imgFile)
            val imageBytes: ByteBuffer = ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
            val img = Image().withBytes(imageBytes)

//                val img = Image().withS3Object(S3Object().withBucket("rekognitiontest1ebb2cbbe7ba4f8d8577c8ebcec769d2-dev").withName("public/biden4.jpg"))
            val request = SearchFacesByImageRequest("faceMatch-dev",img).withFaceMatchThreshold(90F)
            val qq = client.searchFacesByImage(request)
//            TODO: Add downloading of all files so users can view matches
//            qq.faceMatches.forEach({
//                Amplify.Storage.downloadFile()
//            })
            runOnUiThread {
                progress.visibility = View.INVISIBLE

                //Show alert dialog if no results
                if(qq.faceMatches.isEmpty()){
                    showAddDialog(imgFile)
                } else {
                    val numMatches: Int = qq.faceMatches.size
                    val firstSimiliarity: Float = qq.faceMatches[0].similarity
                    val firstExImgId = qq.faceMatches[0].face.externalImageId
                    val firstName = firstExImgId.split("::")[firstExImgId.split("::").size - 1].replace(".jpg","").capitalize()
                    nameText.text = "Number of matches: $numMatches\nMatch quality: $firstSimiliarity\nName: $firstName"

                    matches = ArrayList<FaceMatch>()

                    //Add the external ids to the matches
                    qq.faceMatches.forEach{
                        matches.add(it)
                    }

                    viewMatchesButton.visibility = View.VISIBLE
                }
            }
        } catch(e: Exception){
            Log.e("Testing", "Exception thrown",e)
            runOnUiThread{
                nameText.text = "An error occurred. Please try again"
                progress.visibility = View.INVISIBLE
            }
        }
    }

    private fun showAddDialog(imgFile: File){
        val nameInput = LayoutInflater.from(this@MainActivity).inflate(R.layout.name_dialog, null)

        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setView(nameInput)
        builder.setTitle("Add new face?")
        builder.setMessage("We couldn't find a match for the face in the supplied picture on AWS. Please supply a name to add it to the collection")
        builder.setPositiveButton("Add"){dialog, which ->
            progress.visibility = View.VISIBLE
            CoroutineScope(IO).launch {
                addFaceToCollection(imgFile, nameInput.nameToAdd.text.toString().replace("\\s".toRegex(), ""))
            }
//            Toast.makeText(this@MainActivity, "Adding...", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel"){dialog, which ->
            dialog.dismiss()
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        nameInput.nameToAdd.addTextChangedListener(object: TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int,  count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                Log.i("testyyy", "After change")
                Log.i("testyyy", nameInput.nameToAdd.text.toString())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !nameInput.nameToAdd.text.isNullOrBlank()
            }
        })
    }

    private suspend fun addFaceToCollection(imgFile: File, name: String){
        try{
            //Upload the face to public/indexedFaces and let the lambda function index
            Amplify.Storage.uploadFile(
                "indexedFaces/$name.jpg",
                imgFile,
//            options,
                { result ->
                    Log.i("MyAmplifyApp", "Successfully uploaded: " + result.key)
//                    imgFile.delete()
                },
                { error -> Log.e("MyAmplifyApp", "Upload failed", error) }
            )



//            // Directly index the face without uploading
//            val predictionsPlugin = Amplify.Predictions.getPlugin("awsPredictionsPlugin") as AWSPredictionsPlugin
//            val escapeHatch = predictionsPlugin.escapeHatch
//            val client = escapeHatch.rekognitionClient
//            client.setRegion(com.amazonaws.regions.Region.getRegion(Regions.AP_SOUTHEAST_1))
//
//            val inputStream: InputStream = FileInputStream(imgFile)
//            val imageBytes: ByteBuffer = ByteBuffer.wrap(IOUtils.toByteArray(inputStream))
//            val img = Image().withBytes(imageBytes)
//
////            val iii = Image().withS3Object(S3Object().withBucket("").withName(""))
//
//            val index = IndexFacesRequest("faceMatch-dev", img).withExternalImageId(name)
//            val indexRes = client.indexFaces(index)
//            Log.i("IndexResult", indexRes.toString())


            runOnUiThread{
                progress.visibility = View.INVISIBLE
                Toast.makeText(this@MainActivity, "Added $name to the collection of faces", Toast.LENGTH_SHORT).show()
            }
        } catch(e: Exception){
            Log.i("MainActivity", e.toString())
            runOnUiThread{
                progress.visibility = View.INVISIBLE
                Toast.makeText(this@MainActivity, "There was an error adding the face to the collection. Consult the logs and try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK){
            val auxFile = File(mCurrentPhotoPath)
            val compressedFile = compressImage(auxFile)
            val bitmap: Bitmap = BitmapFactory.decodeFile(compressedFile.absolutePath)
            imageView.setImageBitmap(bitmap)
            compressedPhotoFile = compressedFile
        }
    }

    private fun checkPermission(): Boolean {
        return(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(READ_EXTERNAL_STORAGE, CAMERA),
            PERMISSION_REQUEST_CODE
        )
    }

    @Throws(IOException::class)
    private fun createFile(): File{
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            mCurrentPhotoPath = absolutePath
        }
    }

    private fun listFiles() {
        Log.i("testing", "hellooooooo")
        val options = StorageListOptions.builder()
            .accessLevel(StorageAccessLevel.PROTECTED)
//            .accessLevel(StorageAccessLevel.PROTECTED)
//            .targetIdentityId("")
            .build()

        Amplify.Storage.list(
            "/protected/predictions/index-faces/admin/biden",
            options,
            { result ->
                Log.i("testing", "res: " + result.items)
                result.items.forEach { item ->
                    Log.i("AmplifyApp", "Item: " + item)
                }
            },
            { error -> Log.e("MyAmplifyApp", "List failure", error) }
        )
    }

    private fun translateText(text: String){
        Amplify.Predictions.translateText(
            text,
            LanguageType.ENGLISH,
            LanguageType.FRENCH,
            { result -> Log.i("Testing", result.translatedText) },
            { error -> Log.e("Testing", "Translation failed", error) }
        )
    }

    private fun detectEntities(image: Bitmap){
        Amplify.Predictions.identify(
            IdentifyActionType.DETECT_ENTITIES,
            image,
            { result: IdentifyResult ->
                val identifyResult: IdentifyEntityMatchesResult = result as IdentifyEntityMatchesResult
                val match = identifyResult.entityMatches[0]
                Log.i("AmplifyQuickstart", match.externalImageId)
            },
            { error: PredictionsException ->
                Log.e("AmplifyQuickstart", "Identify failed", error)
            }
        )
    }

    private fun uploadFile() {
        val exampleFile = File(applicationContext.filesDir, "TestFile")
        exampleFile.writeText("Example file contents")

//        val options = StorageUploadFileOptions.builder()
//            .accessLevel(StorageAccessLevel.PROTECTED)
////            .targetIdentityId("")
//            .build()
        Amplify.Storage.uploadFile(
            "predictions/TestFile",
            exampleFile,
//            options,
            { result -> Log.i("MyAmplifyApp", "Successfully uploaded: " + result.key) },
            { error -> Log.e("MyAmplifyApp", "Upload failed", error) }
        )
    }
}