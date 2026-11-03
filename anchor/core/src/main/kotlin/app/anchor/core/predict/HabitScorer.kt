package app.anchor.core.predict

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * IN-01 launch features. Everything is computed on device from usage events and device state;
 * nothing here ever leaves the phone.
 */
data class LaunchFeatures(
    val secondsSinceUnlock: Long,
    /** The app opened before this one was itself a tagged distracting app (app-hopping). */
    val previousWasTagged: Boolean,
    /** The previous foreground app was the launcher / lock screen (an unprompted open). */
    val previousWasLauncher: Boolean,
    val launchesOfThisAppLastHour: Int,
    val hourOfDay: Int,
    val charging: Boolean,
    val screenOnMinutes: Long,
    /** Mean length of the last five sessions in this app, in seconds; null when unknown. */
    val meanRecentSessionSeconds: Double?,
) {
    fun vector(): DoubleArray {
        val angle = 2 * PI * hourOfDay / 24.0
        return doubleArrayOf(
            1.0, // bias
            if (secondsSinceUnlock < 30) 1.0 else 0.0, // opened straight after unlock
            ln(1.0 + secondsSinceUnlock / 60.0) / 5.0,
            if (previousWasTagged) 1.0 else 0.0,
            if (previousWasLauncher) 1.0 else 0.0,
            minOf(launchesOfThisAppLastHour, 12) / 6.0,
            sin(angle),
            cos(angle),
            if (hourOfDay >= 22 || hourOfDay < 2) 1.0 else 0.0,
            if (charging) 1.0 else 0.0,
            ln(1.0 + screenOnMinutes) / 5.0,
            meanRecentSessionSeconds?.let { if (it < 60) 1.0 else 0.0 } ?: 0.0,
        )
    }

    companion object {
        const val SIZE = 12
    }
}

enum class InterventionLevel { NONE, BREATHING_GATE, INTENT_PROMPT }

/**
 * Small on-device logistic model, retrained online from self-supervised labels:
 * an open followed by < 60 s of use, or a back-out after friction, is "habitual";
 * intent-prompt answers and "this was intentional" taps refine it.
 *
 * Phase 2 swaps this for a LiteRT gradient-boosted/MLP model behind the same [score] contract;
 * the hand-set prior keeps behaviour sensible from the first launch.
 */
@Serializable
data class HabitModel(
    val weights: List<Double> = PRIOR,
    val updates: Int = 0,
) {
    init { require(weights.size == LaunchFeatures.SIZE) }

    fun score(f: LaunchFeatures): Double {
        val x = f.vector()
        var z = 0.0
        for (i in x.indices) z += weights[i] * x[i]
        return sigmoid(z)
    }

    /** One SGD step on a labelled launch. [habitual] = true means the open was habitual. */
    fun learn(f: LaunchFeatures, habitual: Boolean, learningRate: Double = 0.05): HabitModel {
        val x = f.vector()
        val err = (if (habitual) 1.0 else 0.0) - score(f)
        val w = weights.mapIndexed { i, wi ->
            val l2 = if (i == 0) 0.0 else 0.001 * wi
            wi + learningRate * (err * x[i] - l2)
        }
        return HabitModel(w, updates + 1)
    }

    companion object {
        val PRIOR: List<Double> = listOf(
            -1.2, // bias: most launches are not interrupted
            1.1,  // opened straight after unlock
            -0.6, // long since unlock
            0.9,  // app-hopping between tagged apps
            0.5,  // from launcher
            1.2,  // repeated launches in the last hour
            0.0, -0.3, // time of day (evenings slightly more habitual)
            0.8,  // late night
            0.1,  // charging (often in bed)
            0.4,  // long screen-on stretch
            0.9,  // recent sessions were short check-ins
        )

        private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))
    }
}

/** Action ladder: low → nothing; medium → breathing gate; high → intent prompt plus a timed fuse. */
data class ActionLadder(
    val mediumThreshold: Double = 0.45,
    val highThreshold: Double = 0.75,
) {
    fun levelFor(score: Double): InterventionLevel = when {
        score >= highThreshold -> InterventionLevel.INTENT_PROMPT
        score >= mediumThreshold -> InterventionLevel.BREATHING_GATE
        else -> InterventionLevel.NONE
    }

    /** 3–5 s breathing gate, longer the closer the score is to the high threshold. */
    fun breathingSeconds(score: Double, maxSeconds: Int): Int {
        val span = (highThreshold - mediumThreshold).coerceAtLeast(1e-6)
        val t = ((score - mediumThreshold) / span).coerceIn(0.0, 1.0)
        val min = minOf(3, maxSeconds)
        return (min + (maxSeconds - min) * t).toInt().coerceAtLeast(1)
    }
}

/** How a launch ended, used to self-label it. */
sealed interface LaunchOutcome {
    /** The user backed out at the breathing gate or intent prompt. */
    data object BackedOut : LaunchOutcome
    /** The user stated a purpose ("message a friend", "look something up"). */
    data object StatedPurpose : LaunchOutcome
    /** The user chose "just browsing". */
    data object JustBrowsing : LaunchOutcome
    /** One-tap "this was intentional" feedback on a false positive. */
    data object MarkedIntentional : LaunchOutcome
    /** No friction shown; label from how long the session lasted. */
    data class Session(val seconds: Long) : LaunchOutcome

    fun isHabitual(): Boolean = when (this) {
        BackedOut, JustBrowsing -> true
        StatedPurpose, MarkedIntentional -> false
        is Session -> seconds < 60
    }
}
