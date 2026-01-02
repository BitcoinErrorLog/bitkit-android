package to.bitkit.paykit.services

import to.bitkit.paykit.models.AutoPayRule
import to.bitkit.paykit.models.AutoPaySettings
import to.bitkit.paykit.models.PeerSpendingLimit
import to.bitkit.paykit.storage.AutoPayStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for evaluating auto-pay rules.
 * This is a standalone service that can be injected into Workers and other components
 * that cannot use ViewModels.
 */
@Singleton
class AutoPayEvaluatorService @Inject constructor(
    private val autoPayStorage: AutoPayStorage,
) : IAutopayEvaluator {

    private var cachedSettings: AutoPaySettings? = null
    private var cachedPeerLimits: List<PeerSpendingLimit> = emptyList()
    private var cachedRules: List<AutoPayRule> = emptyList()

    suspend fun loadSettings() {
        cachedSettings = autoPayStorage.getSettings()
        cachedPeerLimits = autoPayStorage.getPeerLimits()
        cachedRules = autoPayStorage.getRules()
    }

    override fun evaluate(peerPubkey: String, amount: Long, methodId: String): AutopayEvaluationResult {
        val settings = cachedSettings ?: return AutopayEvaluationResult.NeedsApproval

        // If autopay is disabled, require manual approval
        if (!settings.isEnabled) {
            return AutopayEvaluationResult.NeedsApproval
        }

        // Reset daily limits if needed
        val resetSettings = settings.resetIfNeeded()

        // Check global daily limit
        val globalDailyLimit = resetSettings.globalDailyLimitSats
        val currentDailySpent = resetSettings.currentDailySpentSats
        if (currentDailySpent + amount > globalDailyLimit) {
            return AutopayEvaluationResult.Denied("Would exceed daily limit")
        }

        // Check peer-specific limit
        val peerLimit = cachedPeerLimits.firstOrNull { it.peerPubkey == peerPubkey }
        peerLimit?.let { limit ->
            val resetLimit = limit.resetIfNeeded()
            if (resetLimit.spentSats + amount > resetLimit.limitSats) {
                return AutopayEvaluationResult.Denied("Would exceed peer limit")
            }
        }

        // Check auto-pay rules
        val matchingRule = cachedRules.firstOrNull { rule ->
            rule.matches(amount, methodId, peerPubkey)
        }

        if (matchingRule != null) {
            return AutopayEvaluationResult.Approved(
                ruleId = matchingRule.id,
                ruleName = matchingRule.name,
            )
        }

        return AutopayEvaluationResult.NeedsApproval
    }
}

