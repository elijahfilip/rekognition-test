package com.example.rekognitiontest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.services.rekognition.model.FaceMatch
import com.amplifyframework.core.Amplify
import kotlinx.android.synthetic.main.matches.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.io.File

class Matches : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private var myDataset = ArrayList<MatchModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.recycler)

        val matches = intent.extras?.get("matches") as ArrayList<FaceMatch>
        Log.i("Matches", matches.toString())

        CoroutineScope(IO).launch {
            downloadMatches(matches)
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = MyAdapter(myDataset)

        recyclerView = findViewById<RecyclerView>(R.id.my_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home){
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun downloadMatches(matches: ArrayList<FaceMatch>){

        matches.forEach{
            val fileName = it.face.externalImageId.split("::")[it.face.externalImageId.split("::").size - 1]

            Amplify.Storage.downloadFile(
                "indexedFaces/$fileName",
                File("${applicationContext.filesDir}/$fileName"),
                { result ->
                    Log.i("MyAmplifyApp", "Successfully downloaded: ${result.file.name}")
                    val file = File("${applicationContext.filesDir}/$fileName")
                    val bitmap: Bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    val model = MatchModel(fileName.replace(".jpg",""), it.similarity.toString(), bitmap)
                    myDataset.add(model)
                    runOnUiThread{
                        viewAdapter.notifyDataSetChanged()
                    }
                },
                { error -> Log.e("MyAmplifyApp", "Download failure", error)}
            )
        }

//        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//        val imgFile = File(storageDir!!.absolutePath + "/obama.jpg")
//        val bitmap: Bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
//
//        myDataset.add(MatchModel("Test", "98.123123", bitmap))
//        myDataset.add(MatchModel("Test1", "98.1231324234223", bitmap))
//        myDataset.add(MatchModel("Tes2t", "98.43432", bitmap))


    }
}

class MyAdapter(private val myDataset: ArrayList<MatchModel>) : RecyclerView.Adapter<MyAdapter.MyViewHolder>(){
//    class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        fun bindItems(model: MatchModel){
            itemView.matchesImage.setImageBitmap(model.image)
            itemView.matchesName.text = "Name: ${model.name}"
            itemView.matchesSimilarity.text = "Similarity: ${model.similarity}"
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
//        val textView = LayoutInflater.from(parent.context).inflate(R.layout.matches, parent, false) as TextView
        val v = LayoutInflater.from(parent.context).inflate(R.layout.matches, parent, false)
        return MyViewHolder(v)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
//        holder.textView.text = myDataset[position]
        holder.bindItems(myDataset[position])
    }

    override fun getItemCount(): Int {
       return  myDataset.size
    }
}