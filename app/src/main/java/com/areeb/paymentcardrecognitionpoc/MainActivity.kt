package com.areeb.paymentcardrecognitionpoc

import com.areeb.paymentcardrecognitionpoc.IntentStatus.EXPIRED
import android.app.Activity
import android.app.PendingIntent
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.areeb.paymentcardrecognitionpoc.IntentStatus.FAILURE
import com.areeb.paymentcardrecognitionpoc.IntentStatus.INIT_LOADING
import com.areeb.paymentcardrecognitionpoc.IntentStatus.LOADING
import com.areeb.paymentcardrecognitionpoc.IntentStatus.SUCCESS
import com.areeb.paymentcardrecognitionpoc.ui.theme.PaymentCardRecognitionPOCTheme
import com.google.android.gms.wallet.PaymentCardRecognitionIntentRequest
import com.google.android.gms.wallet.PaymentCardRecognitionResult
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants

class MainActivity : ComponentActivity() {

    private lateinit var paymentsClient: PaymentsClient
    private lateinit var cardRecognitionPendingIntent: PendingIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paymentsClient = createPaymentsClient(this)

        setContent {
            PaymentCardRecognitionPOCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(20.dp)
                    ) {
                        MyButton()
                    }
                }
            }
        }
    }

    @Composable
    fun MyButton() {
        var intentStatus by remember { mutableStateOf(INIT_LOADING) }

        LaunchedEffect(intentStatus) {
            when (intentStatus) {
                INIT_LOADING -> createOCRIntent(
                    onSuccess = {
                        intentStatus = SUCCESS
                    },
                    onFailure = {
                        intentStatus = FAILURE
                    },
                )

                else -> {}
            }
        }

        OCRButton(intentStatus) {
            when (intentStatus) {
                INIT_LOADING, LOADING -> {}
                SUCCESS -> {
                    startPaymentCardOcr {
                        intentStatus = EXPIRED
                    }
                }

                EXPIRED, FAILURE -> {
                    intentStatus = LOADING
                    createOCRIntent(
                        onSuccess = {
                            intentStatus = SUCCESS
                            startPaymentCardOcr {
                                intentStatus = EXPIRED
                            }
                        },
                        onFailure = {
                            intentStatus = FAILURE
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun OCRButton(intentStatus: IntentStatus, onButtonClick: () -> Unit) {
        Button(onClick = onButtonClick) {
            when (intentStatus) {
                SUCCESS, EXPIRED -> Text("Open OCR")
                FAILURE -> Text("Unable to load OCR")
                INIT_LOADING, LOADING -> CircularProgressIndicator(
                    color = Color.White,
                )
            }
        }
    }

    private fun createPaymentsClient(activity: Activity): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
            .build()

        return Wallet.getPaymentsClient(activity, walletOptions)
    }

    private fun createOCRIntent(onSuccess: () -> Unit, onFailure: () -> Unit) {
        val request = PaymentCardRecognitionIntentRequest.getDefaultInstance()

        paymentsClient
            .getPaymentCardRecognitionIntent(request)
            .addOnSuccessListener {
                cardRecognitionPendingIntent = it.paymentCardRecognitionPendingIntent
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure()
                Toast.makeText(this, "Payment card ocr not available. $e", Toast.LENGTH_LONG).show()
            }
    }

    private fun startPaymentCardOcr(onComplete: () -> Unit) {
        try {
            val intentSenderForResult =
                IntentSenderRequest.Builder(cardRecognitionPendingIntent.intentSender).build()
            getResultLauncher.launch(intentSenderForResult)
        } catch (e: SendIntentException) {
            Toast.makeText(this, "Failed to start payment card recognition. $e", Toast.LENGTH_LONG)
                .show()
        }
        onComplete()
    }

    private val getResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data

            if (resultCode == Activity.RESULT_OK && data != null) {
                PaymentCardRecognitionResult.getFromIntent(data)?.let {
                    handlePaymentCardRecognitionResult(it)
                }
            }
        }

    private fun handlePaymentCardRecognitionResult(
        cardRecognitionResult: PaymentCardRecognitionResult,
    ) {
        val creditCardExpirationDate = cardRecognitionResult.creditCardExpirationDate
        val expirationDate = creditCardExpirationDate?.let { "%02d/%d".format(it.month, it.year) }
        val cardResultText = "PAN: ${cardRecognitionResult.pan}\nExpiration date: $expirationDate"
        Toast.makeText(this, cardResultText, Toast.LENGTH_LONG).show()
    }
}

enum class IntentStatus {
    INIT_LOADING,
    LOADING,
    SUCCESS,
    EXPIRED,
    FAILURE,
}
