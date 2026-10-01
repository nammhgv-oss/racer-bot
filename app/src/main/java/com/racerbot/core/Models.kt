package com.racerbot.core

enum class GameState { MAIN_MENU, LOADING, STARTING, PLAYING, PAUSED, GAME_OVER, RESTART_SCREEN, SECURITY_CHALLENGE, UNKNOWN }
enum class ThreatLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }
enum class ObjectKind { OBSTACLE, ENERGY }
enum class ControlMode { PROPORTIONAL_DRAG, DISCRETE_SWIPE }
enum class BotMode { IDLE, CALIBRATING, RUNNING, PAUSED }

/** Down-scaled RGB frame. pixels are 0xRRGGBB. */
class Frame(val width: Int, val height: Int, val pixels: IntArray, val timestampMs: Long)

class Blob(
    val x: Int, val y: Int, val w: Int, val h: Int, val area: Int,
    val darkFrac: Float, val yellowFrac: Float, val redFrac: Float
) {
    val bottom: Int get() = y + h
    val right: Int get() = x + w
    val solidity: Float get() = area.toFloat() / (w * h).coerceAtLeast(1)
}

/** Player in processed-frame pixels. */
class PlayerState(val cx: Float, val top: Float, val bottom: Float, val confidence: Float)

/**
 * Road model. extL/extR hold the drivable extent of every processed row (-1 = no road).
 * Positions along the road are expressed "road-relative" (0 = left edge, 1 = right edge),
 * which stays valid through curves and perspective.
 */
class LaneModel(
    val width: Int, val height: Int,
    val extL: IntArray, val extR: IntArray,
    val horizonRow: Int, val roadFractionLower: Float, val confidence: Float
) {
    fun valid(row: Int): Boolean =
        row in 0 until height && extL[row] >= 0 && extR[row] - extL[row] >= 8

    fun nearestValidRow(row: Int): Int {
        var d = 0
        while (d <= height) {
            val a = row - d
            val b = row + d
            if (valid(a)) return a
            if (valid(b)) return b
            if (a < 0 && b >= height) break
            d++
        }
        return -1
    }

    fun toRoad(x: Float, row: Int): Float {
        val r = nearestValidRow(row)
        if (r < 0) return 0.5f
        val l = extL[r].toFloat()
        val w = (extR[r] - extL[r]).toFloat()
        return (x - l) / w
    }

    fun toScreen(rel: Float, row: Int): Float {
        val r = nearestValidRow(row)
        if (r < 0) return width / 2f
        return extL[r] + rel * (extR[r] - extL[r])
    }
}

/** A detected object. rL/rR are road-relative; yTop/yBottom are 0..1 of processed height. */
class Detection(
    val kind: ObjectKind, val blob: Blob,
    val rL: Float, val rR: Float, val yTop: Float, val yBottom: Float, val confidence: Float
)

class EnergyBarState(val segments: Int, val lit: Int, val recognised: Int) {
    val percent: Float get() = if (segments == 0) 0f else lit.toFloat() / segments
    val present: Boolean get() = recognised >= segments - 1
}

class VisionResult(
    val width: Int, val height: Int,
    val player: PlayerState?, val lanes: LaneModel,
    val obstacles: List<Detection>, val energies: List<Detection>,
    val energyBar: EnergyBarState,
    val meanBrightness: Float, val motion: Float, val processingMs: Float
)
