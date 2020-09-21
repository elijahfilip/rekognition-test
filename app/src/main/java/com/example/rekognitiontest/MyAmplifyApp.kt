package com.example.rekognitiontest

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin
import com.amplifyframework.storage.s3.AWSS3StoragePlugin

class MyAmplifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try{
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.addPlugin(AWSPredictionsPlugin())
            Amplify.addPlugin(AWSS3StoragePlugin())
            Amplify.configure(applicationContext)
            Log.i("MyAmplifyApp", "Initialized Amplify")
        } catch (error: AmplifyException){
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error)
        }
    }
}