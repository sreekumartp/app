package app.anchor.core.surface

import app.anchor.core.model.Surface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Detector maintenance rule (FR-05): every in-app surface detector is a versioned, signed rule
 * pack so a YouTube or Instagram UI change is fixed without an app release. Each rule matches on
 * view resource IDs first, then content descriptions, then layout heuristics.
 */
@Serializable
data class RulePack(
    val version: Int,
    val rules: List<SurfaceRule>,
)

@Serializable
data class SurfaceRule(
    val surface: Surface,
    val packageName: String,
    /** Fully-qualified view resource IDs, e.g. "com.google.android.youtube:id/reel_recycler". */
    val viewIds: List<String> = emptyList(),
    val contentDescriptions: List<DescriptionMatch> = emptyList(),
    val heuristics: List<LayoutHeuristic> = emptyList(),
)

@Serializable
data class DescriptionMatch(
    val text: String,
    /** Only match when the node is selected — e.g. the "Shorts" tab while it is the active tab. */
    val requireSelected: Boolean = false,
    val exact: Boolean = false,
) {
    fun matches(node: UiNode): Boolean {
        val d = node.contentDescription ?: return false
        if (requireSelected && !node.isSelected) return false
        return if (exact) d.equals(text, ignoreCase = true) else d.contains(text, ignoreCase = true)
    }
}

/** A node whose class name contains [classNameContains] and covers at least [minScreenFraction] of the screen. */
@Serializable
data class LayoutHeuristic(
    val classNameContains: String,
    val minScreenFraction: Double,
) {
    fun matches(node: UiNode): Boolean =
        node.className?.contains(classNameContains) == true && node.screenFraction >= minScreenFraction
}

/** Read-only view of an accessibility node, so matching is testable off-device. */
interface UiNode {
    val viewId: String?
    val contentDescription: String?
    val className: String?
    val isSelected: Boolean
    /** Fraction of the screen area this node's visible bounds cover, 0..1. */
    val screenFraction: Double
    val childCount: Int
    fun child(index: Int): UiNode?
}

enum class MatchTier { VIEW_ID, CONTENT_DESCRIPTION, HEURISTIC }

data class SurfaceMatch(val surface: Surface, val tier: MatchTier)

class SurfaceMatcher(private val pack: RulePack, private val maxNodes: Int = 600) {

    private val byPackage = pack.rules.groupBy { it.packageName }

    val version: Int get() = pack.version

    fun hasRulesFor(pkg: String) = byPackage.containsKey(pkg)

    /**
     * Returns the first of [wanted] surfaces visible under [root]. Collects a bounded flat list
     * of nodes once, then applies the tiers in order across all rules.
     */
    fun match(pkg: String, root: UiNode, wanted: Set<Surface>): SurfaceMatch? {
        val rules = byPackage[pkg]?.filter { it.surface in wanted }.orEmpty()
        if (rules.isEmpty()) return null
        val nodes = flatten(root)

        for (rule in rules) {
            if (rule.viewIds.isEmpty()) continue
            val ids = rule.viewIds.toHashSet()
            if (nodes.any { it.viewId in ids }) return SurfaceMatch(rule.surface, MatchTier.VIEW_ID)
        }
        for (rule in rules) {
            if (rule.contentDescriptions.any { m -> nodes.any(m::matches) }) {
                return SurfaceMatch(rule.surface, MatchTier.CONTENT_DESCRIPTION)
            }
        }
        for (rule in rules) {
            if (rule.heuristics.any { h -> nodes.any(h::matches) }) {
                return SurfaceMatch(rule.surface, MatchTier.HEURISTIC)
            }
        }
        return null
    }

    private fun flatten(root: UiNode): List<UiNode> {
        val out = ArrayList<UiNode>(64)
        val stack = ArrayDeque<UiNode>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < maxNodes) {
            val n = stack.removeLast()
            out += n
            for (i in n.childCount - 1 downTo 0) n.child(i)?.let(stack::addLast)
        }
        return out
    }
}

/** Signed transport format: [payload] is the RulePack JSON, [signature] a base64 Ed25519 signature over its UTF-8 bytes. */
@Serializable
data class SignedRulePack(val payload: String, val signature: String)

fun interface SignatureVerifier {
    fun verify(message: ByteArray, signature: ByteArray): Boolean
}

/** NFR-07: Ed25519 via the platform JCA provider (JDK 15+, Android 13+ via Conscrypt). */
class Ed25519Verifier(publicKey: PublicKey) : SignatureVerifier {
    private val key = publicKey

    override fun verify(message: ByteArray, signature: ByteArray): Boolean = runCatching {
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(message)
            verify(signature)
        }
    }.getOrDefault(false)

    companion object {
        /** [x509Base64] is a SubjectPublicKeyInfo-encoded Ed25519 public key. */
        fun fromBase64(x509Base64: String): Ed25519Verifier =
            Ed25519Verifier(KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(x509Base64))))
    }
}

sealed interface RulePackResult {
    data class Loaded(val pack: RulePack) : RulePackResult
    data class Rejected(val reason: String) : RulePackResult
}

object RulePackLoader {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Bundled packs ship inside the APK and are trusted as part of the signed app. */
    fun parseBundled(text: String): RulePack = json.decodeFromString(RulePack.serializer(), text)

    /** Remote packs must verify and must not roll back to an older version. */
    fun loadRemote(envelopeJson: String, verifier: SignatureVerifier, currentVersion: Int): RulePackResult {
        val env = runCatching { json.decodeFromString(SignedRulePack.serializer(), envelopeJson) }
            .getOrElse { return RulePackResult.Rejected("malformed envelope") }
        val sig = runCatching { Base64.getDecoder().decode(env.signature) }
            .getOrElse { return RulePackResult.Rejected("malformed signature") }
        if (!verifier.verify(env.payload.toByteArray(Charsets.UTF_8), sig)) return RulePackResult.Rejected("bad signature")
        val pack = runCatching { parseBundled(env.payload) }.getOrElse { return RulePackResult.Rejected("malformed pack") }
        if (pack.version <= currentVersion) return RulePackResult.Rejected("not newer than v$currentVersion")
        return RulePackResult.Loaded(pack)
    }
}
