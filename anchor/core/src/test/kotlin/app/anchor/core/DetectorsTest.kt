package app.anchor.core

import app.anchor.core.doomscroll.DoomscrollDetector
import app.anchor.core.model.Surface
import app.anchor.core.predict.ActionLadder
import app.anchor.core.predict.HabitModel
import app.anchor.core.predict.InterventionLevel
import app.anchor.core.predict.LaunchFeatures
import app.anchor.core.predict.LaunchOutcome
import app.anchor.core.surface.DescriptionMatch
import app.anchor.core.surface.LayoutHeuristic
import app.anchor.core.surface.MatchTier
import app.anchor.core.surface.RulePack
import app.anchor.core.surface.RulePackLoader
import app.anchor.core.surface.RulePackResult
import app.anchor.core.surface.SignatureVerifier
import app.anchor.core.surface.SignedRulePack
import app.anchor.core.surface.SurfaceMatcher
import app.anchor.core.surface.SurfaceRule
import app.anchor.core.surface.UiNode
import app.anchor.core.surface.Ed25519Verifier
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectorsTest {

    @Test fun doomscrollTriggersAfterRapidSwipes() {
        val d = DoomscrollDetector(triggerSwipes = 15)
        var t = 0L
        repeat(14) {
            // Each swipe produces a burst of scroll events.
            d.onScroll("com.app", t); d.onScroll("com.app", t + 100); d.onScroll("com.app", t + 200)
            t += 2_000
        }
        assertFalse(d.state.triggered)
        assertEquals(14, d.state.rapidSwipes)
        d.onScroll("com.app", t)
        assertTrue(d.state.triggered)
        assertTrue(d.state.dimLevel > 0f)
        // Long dwell resets the streak.
        d.onTick(t + 10_000)
        assertFalse(d.state.triggered)
        // Switching apps resets too.
        repeat(20) { d.onScroll("com.app", t + 20_000 + it * 1_000L) }
        assertTrue(d.state.triggered)
        d.onScroll("com.other", t + 50_000)
        assertEquals(1, d.state.rapidSwipes)
    }

    private fun features(sinceUnlock: Long, hops: Boolean, launches: Int, hour: Int, shortSessions: Boolean) =
        LaunchFeatures(sinceUnlock, hops, !hops, launches, hour, charging = false, screenOnMinutes = 20,
            meanRecentSessionSeconds = if (shortSessions) 20.0 else 600.0)

    @Test fun priorScoresHabitualOpensHigher() {
        val m = HabitModel()
        val habitual = m.score(features(5, hops = true, launches = 6, hour = 23, shortSessions = true))
        val intentional = m.score(features(1_800, hops = false, launches = 0, hour = 11, shortSessions = false))
        assertTrue(habitual > 0.75, "habitual=$habitual")
        assertTrue(intentional < 0.45, "intentional=$intentional")
        assertEquals(InterventionLevel.INTENT_PROMPT, ActionLadder().levelFor(habitual))
        assertEquals(InterventionLevel.NONE, ActionLadder().levelFor(intentional))
    }

    @Test fun onlineLearningMovesTowardFeedback() {
        val f = features(5, hops = true, launches = 6, hour = 23, shortSessions = true)
        var m = HabitModel()
        val before = m.score(f)
        repeat(50) { m = m.learn(f, LaunchOutcome.MarkedIntentional.isHabitual()) }
        assertTrue(m.score(f) < before)
        assertEquals(50, m.updates)
        // Round-trips through JSON for on-device persistence.
        val json = Json.encodeToString(HabitModel.serializer(), m)
        assertEquals(m, Json.decodeFromString(HabitModel.serializer(), json))
        assertTrue(LaunchOutcome.Session(30).isHabitual())
        assertFalse(LaunchOutcome.Session(300).isHabitual())
    }

    @Test fun breathingSecondsWithinThreeToFive() {
        val l = ActionLadder()
        assertEquals(3, l.breathingSeconds(0.45, 5))
        assertEquals(5, l.breathingSeconds(0.75, 5))
    }

    private data class Node(
        override val viewId: String? = null,
        override val contentDescription: String? = null,
        override val className: String? = null,
        override val isSelected: Boolean = false,
        override val screenFraction: Double = 0.1,
        val kids: List<Node> = emptyList(),
    ) : UiNode {
        override val childCount get() = kids.size
        override fun child(index: Int) = kids.getOrNull(index)
    }

    private val pack = RulePack(
        version = 1,
        rules = listOf(
            SurfaceRule(Surface.YOUTUBE_SHORTS, "com.google.android.youtube",
                viewIds = listOf("com.google.android.youtube:id/reel_recycler"),
                contentDescriptions = listOf(DescriptionMatch("Shorts", requireSelected = true)),
                heuristics = listOf(LayoutHeuristic("ViewPager", 0.9))),
        ),
    )

    @Test fun surfaceMatcherTiers() {
        val m = SurfaceMatcher(pack)
        val yt = "com.google.android.youtube"
        val wanted = setOf(Surface.YOUTUBE_SHORTS)
        val byId = Node(kids = listOf(Node(kids = listOf(Node(viewId = "com.google.android.youtube:id/reel_recycler")))))
        assertEquals(MatchTier.VIEW_ID, m.match(yt, byId, wanted)?.tier)
        val tabNotSelected = Node(kids = listOf(Node(contentDescription = "Shorts")))
        assertNull(m.match(yt, tabNotSelected, wanted))
        val tabSelected = Node(kids = listOf(Node(contentDescription = "Shorts", isSelected = true)))
        assertEquals(MatchTier.CONTENT_DESCRIPTION, m.match(yt, tabSelected, wanted)?.tier)
        val pager = Node(kids = listOf(Node(className = "androidx.viewpager2.widget.ViewPager2", screenFraction = 0.95)))
        assertEquals(MatchTier.HEURISTIC, m.match(yt, pager, wanted)?.tier)
        assertNull(m.match(yt, byId, emptySet()))
        assertNull(m.match("com.other", byId, wanted))
    }

    @Test fun signedRulePacks() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val payload = Json.encodeToString(RulePack.serializer(), pack.copy(version = 2))
        val sig = Signature.getInstance("Ed25519").run { initSign(kp.private); update(payload.toByteArray()); sign() }
        val env = Json.encodeToString(SignedRulePack.serializer(), SignedRulePack(payload, Base64.getEncoder().encodeToString(sig)))
        val verifier = Ed25519Verifier.fromBase64(Base64.getEncoder().encodeToString(kp.public.encoded))

        val ok = RulePackLoader.loadRemote(env, verifier, currentVersion = 1)
        assertIs<RulePackResult.Loaded>(ok)
        assertEquals(2, ok.pack.version)
        assertIs<RulePackResult.Rejected>(RulePackLoader.loadRemote(env, verifier, currentVersion = 2))
        val tampered = env.replace("reel_recycler", "reel_recyclerX")
        assertIs<RulePackResult.Rejected>(RulePackLoader.loadRemote(tampered, verifier, currentVersion = 1))
        assertIs<RulePackResult.Rejected>(RulePackLoader.loadRemote(env, SignatureVerifier { _, _ -> false }, 1))
    }
}

class BundledRulePackTest {
    @Test fun bundledPackParsesAndCoversEverySurface() {
        val text = java.io.File("../app/src/main/assets/rulepack.json").readText()
        val pack = RulePackLoader.parseBundled(text)
        assertEquals(Surface.entries.toSet(), pack.rules.map { it.surface }.toSet())
        pack.rules.forEach { assertEquals(it.surface.packageName, it.packageName) }
    }
}
