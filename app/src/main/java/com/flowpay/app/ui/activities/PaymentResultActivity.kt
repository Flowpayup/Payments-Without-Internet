// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.flowpay.app.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.flowpay.app.MainActivity
import com.flowpay.app.R
import com.flowpay.app.data.TransactionStatus
import com.flowpay.app.helpers.TransactionDetector
import com.flowpay.app.utils.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentResultActivity : AppCompatActivity() {

    private lateinit var statusCircle: View
    private lateinit var tickImageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var statusExplainerText: TextView
    private lateinit var amountText: TextView
    private lateinit var detailsCard: CardView
    private lateinit var bankNameText: TextView
    private lateinit var transactionIdText: TextView
    private lateinit var dateTimeText: TextView
    private lateinit var upiIdLayout: LinearLayout
    private lateinit var upiIdText: TextView
    private lateinit var recipientLayout: LinearLayout // NEW
    private lateinit var recipientLabel: TextView // NEW
    private lateinit var recipientText: TextView // NEW
    private lateinit var doneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set system UI to black theme
        setupSystemUI()

        setContentView(R.layout.activity_payment_success)

        initViews()
        loadTransactionData()
        startAnimations()

        // Replaces the deprecated onBackPressed() override
        onBackPressedDispatcher.addCallback(this) {
            navigateToMain()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode is singleTask, so a newer payment outcome (e.g. a FAILED
        // confirmation arriving while this screen still shows the previous
        // SUCCESS) is delivered here instead of creating a new instance.
        // Without re-rendering, the screen would keep showing the stale, wrong
        // outcome — contradicting the freshly-posted result notification.
        setIntent(intent)
        resetViewsForAnimation()
        loadTransactionData()
        startAnimations()
    }

    private fun initViews() {
        statusCircle = findViewById(R.id.iv_status_circle)
        tickImageView = findViewById(R.id.iv_success_tick)
        statusText = findViewById(R.id.tv_status)
        statusExplainerText = findViewById(R.id.tv_status_explainer)
        amountText = findViewById(R.id.tv_amount)
        detailsCard = findViewById(R.id.card_details)
        bankNameText = findViewById(R.id.tv_bank_name)
        transactionIdText = findViewById(R.id.tv_transaction_id)
        dateTimeText = findViewById(R.id.tv_date_time)
        upiIdLayout = findViewById(R.id.layout_upi_id)
        upiIdText = findViewById(R.id.tv_upi_id)
        recipientLayout = findViewById(R.id.layout_recipient) // NEW
        recipientLabel = findViewById(R.id.tv_recipient_label) // NEW
        recipientText = findViewById(R.id.tv_recipient_name) // NEW
        doneButton = findViewById(R.id.btn_done)

        resetViewsForAnimation()

        doneButton.setOnClickListener {
            navigateToMain()
        }
    }

    /** Return the animated views to their pre-animation (hidden) state. */
    private fun resetViewsForAnimation() {
        tickImageView.alpha = 0f
        statusText.alpha = 0f
        statusExplainerText.alpha = 0f
        amountText.alpha = 0f
        detailsCard.alpha = 0f
        doneButton.alpha = 0f
    }

    private fun loadTransactionData() {
        val transactionId = intent.getStringExtra("transaction_id") ?: "N/A"
        val amount = intent.getStringExtra("amount") ?: "0"
        val status = intent.getStringExtra("status") ?: "UNKNOWN"
        val bankName = intent.getStringExtra("bank_name") ?: "Bank"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val upiId = intent.getStringExtra("upi_id")
        val transactionType = intent.getStringExtra("transaction_type") ?: "DEBIT"
        val recipientName = intent.getStringExtra("recipient_name") // NEW
        val phoneNumber = intent.getStringExtra("phone_number") // NEW

        // Get operation type from detector
        val detector = TransactionDetector.getInstance(this)
        val operationType = detector.getOperationType() ?: ""

        // Render the outcome the bank actually reported — this screen is
        // launched for every parsed confirmation, not only successes. Every
        // property is set explicitly (never relying on layout defaults) so an
        // onNewIntent re-render from a different status resets cleanly.
        //
        // Color language: SUCCESS wears the brand look (blue gradient circle,
        // blue heading, green amount). Non-success outcomes wear their status
        // color on circle + heading + amount so a FAILED result can never be
        // mistaken for a success at a glance. The glyph is always white.
        when (status) {
            TransactionStatus.FAILED -> {
                statusText.text = getString(R.string.payment_status_failed)
                statusExplainerText.text = getString(R.string.status_explainer_failed)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_error)
                applyStatusAccent(R.color.error_red)
            }
            TransactionStatus.NEEDS_REVIEW -> {
                statusText.text = getString(R.string.payment_status_needs_review)
                statusExplainerText.text = getString(R.string.status_explainer_needs_review)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_error)
                applyStatusAccent(R.color.warning_orange)
            }
            TransactionStatus.SUCCESS -> {
                statusText.text = getString(R.string.payment_status_success)
                statusExplainerText.visibility = View.GONE
                tickImageView.setImageResource(R.drawable.ic_check_white)
                applySuccessAccent()
            }
            else -> {
                // UNVERIFIED, or any unexpected status — the outcome is not a
                // confirmed success, so render it neutral (grey, question glyph)
                // and NEVER fall open to the green success look. Only an explicit
                // SUCCESS above earns the success styling.
                statusText.text = getString(R.string.payment_status_unverified)
                statusExplainerText.text = getString(R.string.status_explainer_unverified)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_unverified)
                applyStatusAccent(R.color.unverified_grey)
            }
        }
        tickImageView.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
        amountText.text = getString(R.string.amount_rupees, formatAmount(amount))

        // Handle recipient/sender display - UPDATED LOGIC
        when {
            !recipientName.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(counterpartyLabelFor(status, transactionType))
                recipientText.text = recipientName
            }
            !phoneNumber.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(
                    if (transactionType == "CREDIT") R.string.recipient_from else R.string.recipient_to
                )
                recipientText.text = phoneNumber
            }
            operationType == "UPI_123" && detector.getPhoneNumber() != null -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(R.string.recipient_to)
                recipientText.text = detector.getPhoneNumber()
            }
            else -> {
                recipientLayout.visibility = View.GONE
            }
        }

        // Bank name - show if different from recipient
        bankNameText.text = bankName

        transactionIdText.text = bankReferenceOf(transactionId)
        dateTimeText.text = formatDateTime(timestamp)

        // Show UPI ID if available
        if (!upiId.isNullOrEmpty()) {
            upiIdLayout.visibility = View.VISIBLE
            upiIdText.text = upiId
        } else {
            upiIdLayout.visibility = View.GONE
        }
    }

    /** SUCCESS look: brand-blue gradient circle, blue heading, green amount. */
    private fun applySuccessAccent() {
        statusCircle.backgroundTintList = null
        statusCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_success_bg)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.transaction_primary))
        amountText.setTextColor(ContextCompat.getColor(this, R.color.flowpay_green))
    }

    /** Non-success look: one status colour across circle, heading and amount. */
    private fun applyStatusAccent(colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        statusCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_status_bg)
        statusCircle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        statusText.setTextColor(color)
        amountText.setTextColor(color)
    }

    private fun formatAmount(amount: String): String = CurrencyFormat.inr(amount)

    /**
     * The label above the counterparty's name, for the outcome being rendered.
     *
     * "Paid to" and "Received from" are past tense: they assert that the money
     * moved, so only a confirmed SUCCESS earns them. Under a red "Payment
     * Failed" heading the assertion is simply false — the card read "Paid to
     * SHARMA STORE" directly below an explainer saying any debited amount is
     * normally auto-reversed, so the screen contradicted itself and the more
     * concrete-looking line is the one a worried user believes. NEEDS_REVIEW
     * and UNVERIFIED have the same problem for the opposite reason: nobody
     * knows yet whether it moved, which is the whole point of those states.
     *
     * The neutral "To"/"From" names the counterparty without claiming an
     * outcome, which is all the row was ever there to do.
     */
    private fun counterpartyLabelFor(status: String, transactionType: String): Int {
        val isCredit = transactionType == "CREDIT"
        return when {
            status != TransactionStatus.SUCCESS ->
                if (isCredit) R.string.recipient_from else R.string.recipient_to
            isCredit -> R.string.recipient_received_from
            else -> R.string.recipient_paid_to
        }
    }

    /**
     * The part of a stored transaction id a user can actually act on.
     *
     * Ids are stored as `<bank reference>_<timestamp>` — the timestamp keeps
     * rows unique when a bank reuses a reference, and is meaningless to the
     * reader. Shown whole it produced "125012501250…85952823651", ellipsised
     * through the middle, which is precisely the half a user needs to match
     * this payment against their bank statement. Generated ids (`TXN…`, no
     * separator) are shown as-is.
     */
    private fun bankReferenceOf(transactionId: String): String =
        transactionId.substringBeforeLast('_').ifBlank { transactionId }

    private fun formatDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun startAnimations() {
        // Animate tick with draw effect
        Handler(Looper.getMainLooper()).postDelayed({
            animateTickMark()
        }, 300)

        // Fade in status text (and its explainer line, when visible)
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
            statusExplainerText.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 800)

        // Fade in amount
        Handler(Looper.getMainLooper()).postDelayed({
            amountText.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(400)
                .withEndAction {
                    amountText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }, 1200)

        // Slide up details card
        Handler(Looper.getMainLooper()).postDelayed({
            detailsCard.translationY = 100f
            detailsCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, 1600)

        // Fade in done button
        Handler(Looper.getMainLooper()).postDelayed({
            doneButton.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 2000)
    }

    private fun animateTickMark() {
        tickImageView.alpha = 1f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1000
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            // Create custom tick drawing animation
            tickImageView.scaleX = progress
            tickImageView.scaleY = progress
            tickImageView.rotation = progress * 360f
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tickImageView.rotation = 0f
                // Pulse animation after tick completes
                tickImageView.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction {
                        tickImageView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
        })

        animator.start()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun setupSystemUI() {
        // Make status bar and navigation bar black (minSdk 29 — no guard needed)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // Black nav bar => light (white) buttons, via the compat controller
        // instead of deprecated direct systemUiVisibility manipulation.
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightNavigationBars = false

        // Hide status bar and navigation bar for immersive experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // API 29 has no WindowInsetsController — the legacy flags stay.
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupSystemUI()
        }
    }
}
