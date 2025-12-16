package to.bitkit.paykit.services

import to.bitkit.paykit.storage.AutoPayStorage
import to.bitkit.paykit.storage.SpendingLimitStorage
import to.bitkit.paykit.workers.AutoPayDecision
import to.bitkit.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates auto-pay rules and spending limits for incoming payment requests.
 */
@Singleton
class AutoPayEvaluator @Inject constructor(
    private val autoPayStorage: AutoPayStorage,
    private val spendingLimitStorage: SpendingLimitStorage,
) {
    companion object {
        private const val TAG = "AutoPayEvaluator"
    }

    /**
     * Evaluate whether an incoming payment request should be auto-approved.
     *
     * @param peerPubkey The pubkey of the payment requester
     * @param amountSats The requested amount in satoshis
     * @param methodId Optional payment method ID
     * @return AutoPayDecision indicating whether to approve, deny, or require manual approval
     */
    suspend fun evaluate(
        peerPubkey: String,
        amountSats: Long,
        methodId: String?,
    ): AutoPayDecision {
        Logger.info("Evaluating auto-pay for peer=$peerPubkey, amount=$amountSats sats", context = TAG)

        // 1. Check if auto-pay is globally enabled
        if (!autoPayStorage.isAutoPayEnabled()) {
            Logger.info("Auto-pay is globally disabled", context = TAG)
            return AutoPayDecision.NeedsManualApproval
        }

        // 2. Check if there's a specific rule for this peer
        val peerRule = autoPayStorage.getAutoPayRuleForPeer(peerPubkey)
        if (peerRule != null) {
            if (!peerRule.enabled) {
                Logger.info("Auto-pay disabled for peer $peerPubkey", context = TAG)
                return AutoPayDecision.Denied("Auto-pay disabled for this peer")
            }

            // Check per-transaction limit
            if (peerRule.maxAmountPerTransaction > 0 && amountSats > peerRule.maxAmountPerTransaction) {
                Logger.info(
                    "Amount $amountSats exceeds per-transaction limit ${peerRule.maxAmountPerTransaction}",
                    context = TAG,
                )
                return AutoPayDecision.Denied("Amount exceeds per-transaction limit")
            }
        }

        // 3. Check spending limits
        val spendingLimit = spendingLimitStorage.getSpendingLimitForPeer(peerPubkey)
        if (spendingLimit != null) {
            // Check if we would exceed the spending limit
            if (spendingLimit.wouldExceedLimit(amountSats)) {
                val remaining = spendingLimit.remainingSats()
                Logger.info(
                    "Would exceed spending limit: requesting $amountSats, remaining $remaining",
                    context = TAG,
                )
                return AutoPayDecision.Denied("Would exceed spending limit ($remaining sats remaining)")
            }
        }

        // 4. Check global spending limit
        val globalLimit = spendingLimitStorage.getGlobalSpendingLimit()
        if (globalLimit != null && globalLimit.wouldExceedLimit(amountSats)) {
            val remaining = globalLimit.remainingSats()
            Logger.info(
                "Would exceed global spending limit: requesting $amountSats, remaining $remaining",
                context = TAG,
            )
            return AutoPayDecision.Denied("Would exceed global spending limit ($remaining sats remaining)")
        }

        // 5. All checks passed - approve
        val ruleName = peerRule?.name ?: "default"
        Logger.info("Auto-pay approved by rule: $ruleName", context = TAG)
        return AutoPayDecision.Approved(ruleName)
    }
}

/**
 * Auto-pay rule for a specific peer
 */
data class AutoPayRule(
    val peerPubkey: String,
    val name: String,
    val enabled: Boolean,
    val maxAmountPerTransaction: Long, // 0 = no limit
    val requireConfirmation: Boolean,
)

/**
 * Spending limit configuration
 */
data class SpendingLimit(
    val totalLimitSats: Long,
    val currentSpentSats: Long,
    val period: String, // "daily", "weekly", "monthly"
    val lastResetTimestamp: Long,
) {
    fun wouldExceedLimit(amountSats: Long): Boolean = (currentSpentSats + amountSats) > totalLimitSats

    fun remainingSats(): Long = maxOf(0, totalLimitSats - currentSpentSats)
}

