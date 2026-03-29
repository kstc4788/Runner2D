package com.example.runner2d

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Obstacle(
    var x: Float,
    val width: Float,
    val height: Float,
    val y: Float,
    val type: ObstacleType,
)

enum class ObstacleType {
    BLOCK_LOW,
    BLOCK_TALL,
    DRONE,
}

enum class PowerUpType {
    SHIELD,
    SLOW_MO,
    DOUBLE_JUMP,
}

enum class Sfx {
    JUMP,
    DOUBLE_JUMP,
    PICKUP,
    SHIELD_BREAK,
    GAME_OVER,
    REVIVE,
    UI_TAP,
}

data class PowerUpItem(
    var x: Float,
    val y: Float,
    val radius: Float,
    val type: PowerUpType,
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
)

data class DailyProgress(
    val dateKey: String,
    val jumps: Int,
    val powerups: Int,
    val bestScore: Int,
)

private fun todayKey(): String {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    return fmt.format(Date())
}

private fun loadDailyProgress(prefs: android.content.SharedPreferences): DailyProgress {
    val today = todayKey()
    val savedDate = prefs.getString("daily_date", today) ?: today
    return if (savedDate == today) {
        DailyProgress(
            dateKey = today,
            jumps = prefs.getInt("daily_jumps", 0),
            powerups = prefs.getInt("daily_powerups", 0),
            bestScore = prefs.getInt("daily_best_score", 0),
        )
    } else {
        val fresh = DailyProgress(today, 0, 0, 0)
        saveDailyProgress(prefs, fresh)
        fresh
    }
}

private fun saveDailyProgress(
    prefs: android.content.SharedPreferences,
    progress: DailyProgress,
) {
    prefs.edit()
        .putString("daily_date", progress.dateKey)
        .putInt("daily_jumps", progress.jumps)
        .putInt("daily_powerups", progress.powerups)
        .putInt("daily_best_score", progress.bestScore)
        .apply()
}

private fun vibrateGameOver(context: Context) {
    val durationMs = 120L
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            if (manager.defaultVibrator.hasVibrator()) {
                manager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durationMs)
            }
        }
    } catch (_: SecurityException) {
        // Haptics disabled by platform policy or missing permission at runtime.
    }
}

private fun circleIntersectsRect(cx: Float, cy: Float, radius: Float, rect: Rect): Boolean {
    val nearestX = cx.coerceIn(rect.left, rect.right)
    val nearestY = cy.coerceIn(rect.top, rect.bottom)
    val dx = cx - nearestX
    val dy = cy - nearestY
    return dx * dx + dy * dy <= radius * radius
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("runner2d_prefs", Context.MODE_PRIVATE) }
    val adsManager = remember(context) { RunnerAdsManager(context) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }

    var audioEnabled by remember { mutableStateOf(prefs.getBoolean("audio_enabled", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }
    var tutorialVisible by remember { mutableStateOf(!prefs.getBoolean("tutorial_seen", false)) }
    var dailyProgress by remember { mutableStateOf(loadDailyProgress(prefs)) }
    var completedRuns by remember { mutableIntStateOf(prefs.getInt("completed_runs", 0)) }
    var usedSecondChanceThisRun by remember { mutableStateOf(false) }

    val missionScoreTarget = 120
    val missionJumpsTarget = 25
    val missionPowerupsTarget = 3

    fun refreshDailyIfNeeded() {
        val current = todayKey()
        if (dailyProgress.dateKey != current) {
            val fresh = DailyProgress(current, 0, 0, 0)
            dailyProgress = fresh
            saveDailyProgress(prefs, fresh)
        }
    }

    fun updateDaily(transform: (DailyProgress) -> DailyProgress) {
        refreshDailyIfNeeded()
        val updated = transform(dailyProgress)
        dailyProgress = updated
        saveDailyProgress(prefs, updated)
    }

    fun playSfx(sfx: Sfx) {
        if (!audioEnabled) return
        when (sfx) {
            Sfx.JUMP -> tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 70)
            Sfx.DOUBLE_JUMP -> tone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
            Sfx.PICKUP -> tone.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            Sfx.SHIELD_BREAK -> tone.startTone(ToneGenerator.TONE_PROP_NACK, 90)
            Sfx.GAME_OVER -> tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
            Sfx.REVIVE -> tone.startTone(ToneGenerator.TONE_PROP_ACK, 90)
            Sfx.UI_TAP -> tone.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        }
    }

    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }

    LaunchedEffect(Unit) {
        adsManager.preloadAll()
    }

    val groundHeightPx = with(LocalDensity.current) { 100.dp.toPx() }
    val playerSize = with(LocalDensity.current) { 48.dp.toPx() }
    val bannerTapGuardPx = with(LocalDensity.current) { 80.dp.toPx() }

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    var playerY by remember { mutableFloatStateOf(0f) }
    var playerVelocity by remember { mutableFloatStateOf(0f) }
    var airJumpsUsed by remember { mutableIntStateOf(0) }
    var runningPhase by remember { mutableFloatStateOf(0f) }
    var coyoteTimer by remember { mutableFloatStateOf(0f) }
    var jumpBufferTimer by remember { mutableFloatStateOf(0f) }
    var cameraShakeTimer by remember { mutableFloatStateOf(0f) }

    var sessionJumps by remember { mutableIntStateOf(0) }
    var sessionPowerups by remember { mutableIntStateOf(0) }

    val gravity = 2400f
    val jumpImpulse = -980f
    val baseObstacleSpeed = 420f

    val obstacles = remember { mutableStateListOf<Obstacle>() }
    var waveTimer by remember { mutableFloatStateOf(0f) }
    var nextWaveIn by remember { mutableFloatStateOf(1.6f) }
    val particles = remember { mutableStateListOf<Particle>() }

    val powerUps = remember { mutableStateListOf<PowerUpItem>() }
    var powerSpawnTimer by remember { mutableFloatStateOf(0f) }
    var nextPowerSpawnIn by remember { mutableFloatStateOf(6.0f) }

    var shieldTimer by remember { mutableFloatStateOf(0f) }
    var slowMoTimer by remember { mutableFloatStateOf(0f) }
    var doubleJumpTimer by remember { mutableFloatStateOf(0f) }

    var score by remember { mutableFloatStateOf(0f) }
    var bestScore by remember { mutableFloatStateOf(prefs.getFloat("best_score", 0f)) }
    var started by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    var farLayerOffset by remember { mutableFloatStateOf(0f) }
    var nearLayerOffset by remember { mutableFloatStateOf(0f) }
    var cloudOffset by remember { mutableFloatStateOf(0f) }

    var frameTicker by remember { mutableFloatStateOf(0f) }

    val stars = remember {
        listOf(
            Offset(0.08f, 0.15f), Offset(0.18f, 0.11f), Offset(0.29f, 0.18f), Offset(0.41f, 0.09f),
            Offset(0.53f, 0.16f), Offset(0.65f, 0.10f), Offset(0.76f, 0.17f), Offset(0.88f, 0.12f),
        )
    }

    fun continueAfterReward() {
        gameOver = false
        isPaused = false
        usedSecondChanceThisRun = true
        shieldTimer = max(shieldTimer, 2.5f)
        val playerFrontX = canvasWidth * 0.2f + playerSize + 40f
        obstacles.removeAll { it.x < playerFrontX }
        playSfx(Sfx.REVIVE)
    }

    fun spawnObstacleForWave(floorY: Float, difficulty: Float) {
        val estimatedSpeed = baseObstacleSpeed * difficulty
        // Enforce a guaranteed playable gap between consecutive obstacles.
        val baseGapPx = (estimatedSpeed * 0.50f).coerceIn(185f, 320f)
        val nearestAhead = obstacles.maxByOrNull { it.x + it.width }
        val lastRight = nearestAhead?.let { it.x + it.width } ?: 0f
        val extraGap = if (nearestAhead?.type == ObstacleType.BLOCK_TALL) 22f else 0f
        val spawnX = max(canvasWidth + 26f, lastRight + baseGapPx + extraGap)

        val roll = Random.nextFloat()
        val droneChance = (0.16f + (difficulty - 1f) * 0.16f).coerceIn(0.16f, 0.34f)
        val tallChance = (0.23f + (difficulty - 1f) * 0.12f).coerceIn(0.23f, 0.35f)
        var type = when {
            roll < droneChance -> ObstacleType.DRONE
            roll < droneChance + tallChance -> ObstacleType.BLOCK_TALL
            else -> ObstacleType.BLOCK_LOW
        }
        if (score < 65f && type == ObstacleType.BLOCK_TALL && Random.nextFloat() < 0.5f) {
            type = ObstacleType.BLOCK_LOW
        }
        val obstacle = when (type) {
            ObstacleType.BLOCK_LOW -> {
                val h = Random.nextFloat() * 28f + 52f
                val w = Random.nextFloat() * 22f + 40f
                Obstacle(spawnX, w, h, floorY + playerSize - h, type)
            }
            ObstacleType.BLOCK_TALL -> {
                val h = Random.nextFloat() * 36f + 88f
                val w = Random.nextFloat() * 18f + 32f
                Obstacle(spawnX, w, h, floorY + playerSize - h, type)
            }
            ObstacleType.DRONE -> {
                val h = Random.nextFloat() * 18f + 28f
                val w = Random.nextFloat() * 16f + 30f
                val y = floorY - (Random.nextFloat() * (playerSize * 0.85f) + playerSize * 0.42f)
                Obstacle(spawnX, w, h, y, type)
            }
        }
        obstacles.add(obstacle)
    }

    fun spawnImpactParticles(x: Float, y: Float, color: Color) {
        repeat(9) {
            val speedX = Random.nextFloat() * 220f - 110f
            val speedY = -(Random.nextFloat() * 190f + 40f)
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = speedX,
                    vy = speedY,
                    life = 0.45f,
                    maxLife = 0.45f,
                    color = color,
                ),
            )
        }
    }

    fun queueJump() {
        jumpBufferTimer = 0.12f
    }

    fun resetGame() {
        refreshDailyIfNeeded()
        obstacles.clear()
        particles.clear()
        powerUps.clear()
        waveTimer = 0f
        nextWaveIn = 1.6f
        powerSpawnTimer = 0f
        nextPowerSpawnIn = 6.0f
        score = 0f
        gameOver = false
        isPaused = false
        started = true
        playerVelocity = jumpImpulse * 0.6f
        airJumpsUsed = 0
        runningPhase = 0f
        coyoteTimer = 0f
        jumpBufferTimer = 0f
        cameraShakeTimer = 0f
        sessionJumps = 0
        sessionPowerups = 0
        farLayerOffset = 0f
        nearLayerOffset = 0f
        cloudOffset = 0f
        shieldTimer = 0f
        slowMoTimer = 0f
        doubleJumpTimer = 0f
        usedSecondChanceThisRun = false
    }

    val showStaticOverlay = !started || isPaused || gameOver

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1C2F))
            .pointerInput(gameOver, started, isPaused, doubleJumpTimer, airJumpsUsed, showStaticOverlay) {
                detectTapGestures(onTap = { offset ->
                    if (showStaticOverlay && canvasHeight > 0f && offset.y > canvasHeight - bannerTapGuardPx) {
                        return@detectTapGestures
                    }
                    if (!started) {
                        if (tutorialVisible) {
                            tutorialVisible = false
                            prefs.edit().putBoolean("tutorial_seen", true).apply()
                            playSfx(Sfx.UI_TAP)
                            return@detectTapGestures
                        }
                        resetGame()
                    } else if (gameOver) {
                        completedRuns += 1
                        prefs.edit().putInt("completed_runs", completedRuns).apply()
                        if (activity != null) {
                            adsManager.maybeShowInterstitial(
                                activity = activity,
                                completedRuns = completedRuns,
                                frequencyCap = 4,
                            )
                        }
                        resetGame()
                    } else if (!isPaused) {
                        queueJump()
                    }
                })
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTicker
            canvasWidth = size.width
            canvasHeight = size.height

            val floorY = size.height - groundHeightPx - playerSize
            if (!started) {
                playerY = floorY
            }

            val earlyRamp = (score / 170f).coerceAtMost(1.3f)
            val lateRamp = ((score - 180f).coerceAtLeast(0f) / 260f).coerceAtMost(0.85f)
            val difficulty = 1f + earlyRamp * 0.62f + lateRamp
            val slowMoFactor = if (slowMoTimer > 0f) 0.65f else 1f
            val obstacleSpeed = baseObstacleSpeed * difficulty * slowMoFactor

            if (started && !gameOver && !isPaused) {
                val dt = 1f / 60f

                if (shieldTimer > 0f) shieldTimer = max(0f, shieldTimer - dt)
                if (slowMoTimer > 0f) slowMoTimer = max(0f, slowMoTimer - dt)
                if (doubleJumpTimer > 0f) doubleJumpTimer = max(0f, doubleJumpTimer - dt)
                if (coyoteTimer > 0f) coyoteTimer = max(0f, coyoteTimer - dt)
                if (jumpBufferTimer > 0f) jumpBufferTimer = max(0f, jumpBufferTimer - dt)
                if (cameraShakeTimer > 0f) cameraShakeTimer = max(0f, cameraShakeTimer - dt)

                val onGroundBeforePhysics = playerY >= floorY - 1f
                if (onGroundBeforePhysics) {
                    coyoteTimer = 0.10f
                }

                val canGroundJump = onGroundBeforePhysics || coyoteTimer > 0f
                val canAirJump = doubleJumpTimer > 0f && airJumpsUsed < 1 && !onGroundBeforePhysics
                if (jumpBufferTimer > 0f && (canGroundJump || canAirJump)) {
                    playerVelocity = if (canAirJump && !canGroundJump) jumpImpulse * 0.92f else jumpImpulse
                    if (canAirJump && !canGroundJump) {
                        airJumpsUsed += 1
                        playSfx(Sfx.DOUBLE_JUMP)
                    } else {
                        airJumpsUsed = 0
                        playSfx(Sfx.JUMP)
                    }
                    sessionJumps += 1
                    updateDaily { it.copy(jumps = it.jumps + 1) }
                    jumpBufferTimer = 0f
                    coyoteTimer = 0f
                }

                playerVelocity += gravity * dt
                playerY += playerVelocity * dt

                if (playerY > floorY) {
                    playerY = floorY
                    playerVelocity = 0f
                    airJumpsUsed = 0
                }

                if (playerY >= floorY - 1f) {
                    runningPhase += dt * (12f * difficulty)
                }

                farLayerOffset = (farLayerOffset + 28f * dt * difficulty) % size.width
                nearLayerOffset = (nearLayerOffset + 74f * dt * difficulty) % size.width
                cloudOffset = (cloudOffset + 16f * dt) % (size.width + 220f)

                waveTimer += dt
                if (waveTimer >= nextWaveIn) {
                    waveTimer = 0f
                    val earlyGrace = if (score < 95f) 0.26f else 0f
                    nextWaveIn = (Random.nextFloat() * 0.45f + 1.2f + earlyGrace - (difficulty - 1f) * 0.22f)
                        .coerceIn(0.78f, 1.9f)
                    spawnObstacleForWave(floorY, difficulty)
                }

                powerSpawnTimer += dt
                if (powerSpawnTimer >= nextPowerSpawnIn) {
                    powerSpawnTimer = 0f
                    nextPowerSpawnIn = Random.nextFloat() * 3.5f + 5.2f
                    val roll = Random.nextFloat()
                    val type = when {
                        roll < 0.38f -> PowerUpType.SHIELD
                        roll < 0.70f -> PowerUpType.SLOW_MO
                        else -> PowerUpType.DOUBLE_JUMP
                    }
                    val y = floorY - (Random.nextFloat() * 110f + 30f)
                    powerUps.add(PowerUpItem(size.width + 40f, y, 14f, type))
                }

                obstacles.forEach { it.x -= obstacleSpeed * dt }
                obstacles.removeAll { it.x + it.width < -20f }

                powerUps.forEach { it.x -= obstacleSpeed * dt }
                powerUps.removeAll { it.x + it.radius < -20f }

                particles.forEach { p ->
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += gravity * 0.45f * dt
                    p.life -= dt
                }
                particles.removeAll { it.life <= 0f }

                val playerRect = Rect(
                    left = size.width * 0.2f,
                    top = playerY,
                    right = size.width * 0.2f + playerSize,
                    bottom = playerY + playerSize,
                )

                val collected = mutableListOf<PowerUpItem>()
                for (item in powerUps) {
                    if (circleIntersectsRect(item.x, item.y, item.radius, playerRect)) {
                        when (item.type) {
                            PowerUpType.SHIELD -> shieldTimer = 8f
                            PowerUpType.SLOW_MO -> slowMoTimer = 5f
                            PowerUpType.DOUBLE_JUMP -> doubleJumpTimer = 10f
                        }
                        sessionPowerups += 1
                        updateDaily { it.copy(powerups = it.powerups + 1) }
                        playSfx(Sfx.PICKUP)
                        collected.add(item)
                    }
                }
                powerUps.removeAll(collected)

                var hitObstacle: Obstacle? = null
                for (obstacle in obstacles) {
                    val obstacleRect = Rect(
                        left = obstacle.x,
                        top = obstacle.y,
                        right = obstacle.x + obstacle.width,
                        bottom = obstacle.y + obstacle.height,
                    )
                    if (playerRect.overlaps(obstacleRect)) {
                        hitObstacle = obstacle
                        break
                    }
                }

                if (hitObstacle != null) {
                    if (shieldTimer > 0f) {
                        shieldTimer = 0f
                        spawnImpactParticles(
                            x = hitObstacle.x + hitObstacle.width * 0.5f,
                            y = hitObstacle.y + hitObstacle.height * 0.5f,
                            color = Color(0xFF7DE2FF),
                        )
                        obstacles.remove(hitObstacle)
                        cameraShakeTimer = max(cameraShakeTimer, 0.14f)
                        playSfx(Sfx.SHIELD_BREAK)
                    } else {
                        gameOver = true
                        spawnImpactParticles(
                            x = playerRect.right,
                            y = playerRect.center.y,
                            color = Color(0xFFFF8C8C),
                        )
                        cameraShakeTimer = 0.25f
                        bestScore = max(bestScore, score)
                        prefs.edit().putFloat("best_score", bestScore).apply()
                    }
                }

                score += dt * 10f * (1f + (difficulty - 1f) * 0.4f)
                val scoreInt = score.toInt()
                if (scoreInt > dailyProgress.bestScore) {
                    updateDaily { it.copy(bestScore = scoreInt) }
                }
            }

            val shakeStrength = if (cameraShakeTimer > 0f) (cameraShakeTimer / 0.25f) * 8f else 0f
            val shakeX = if (shakeStrength > 0f) Random.nextFloat() * shakeStrength * 2f - shakeStrength else 0f
            val shakeY = if (shakeStrength > 0f) Random.nextFloat() * shakeStrength * 2f - shakeStrength else 0f

            drawRect(color = Color(0xFF0E1C2F), size = size)

            stars.forEach { s ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 2.4f,
                    center = Offset(size.width * s.x, size.height * s.y),
                )
            }

            for (i in -1..2) {
                val x = i * size.width - farLayerOffset
                drawCircle(
                    color = Color(0xFF1D3553),
                    radius = size.width * 0.45f,
                    center = Offset(x, size.height - groundHeightPx + 95f),
                )
            }

            for (i in -1..2) {
                val x = i * size.width - nearLayerOffset
                drawCircle(
                    color = Color(0xFF224B70),
                    radius = size.width * 0.33f,
                    center = Offset(x, size.height - groundHeightPx + 72f),
                )
            }

            for (i in 0..2) {
                val cloudX = (i * (size.width * 0.45f)) + 120f - cloudOffset
                val cloudY = 110f + i * 30f
                drawCircle(Color(0x33FFFFFF), 28f, Offset(cloudX, cloudY))
                drawCircle(Color(0x33FFFFFF), 22f, Offset(cloudX + 30f, cloudY + 8f))
                drawCircle(Color(0x33FFFFFF), 20f, Offset(cloudX - 25f, cloudY + 8f))
            }

            drawRect(
                color = Color(0xFF153A5B),
                topLeft = Offset(0f, size.height - groundHeightPx),
                size = Size(size.width, groundHeightPx),
            )

            val playerX = size.width * 0.2f + shakeX
            val bodyY = playerY + playerSize * 0.25f + shakeY
            val legHeight = playerSize * 0.22f
            val legPhase = if (playerY >= floorY - 1f) sin(runningPhase) else 0f
            val legOffset = legPhase * (playerSize * 0.08f)

            drawRoundRect(
                color = Color(0xFF57C57A),
                topLeft = Offset(playerX + playerSize * 0.14f, bodyY),
                size = Size(playerSize * 0.72f, playerSize * 0.55f),
                cornerRadius = CornerRadius(12f, 12f),
            )
            drawCircle(
                color = Color(0xFF6FD08C),
                radius = playerSize * 0.20f,
                center = Offset(playerX + playerSize * 0.52f, playerY + playerSize * 0.21f + shakeY),
            )
            drawCircle(
                color = Color(0xFF0E1C2F),
                radius = 3.4f,
                center = Offset(playerX + playerSize * 0.59f, playerY + playerSize * 0.20f + shakeY),
            )
            drawRoundRect(
                color = Color(0xFF2E8A57),
                topLeft = Offset(playerX + playerSize * 0.23f + legOffset, playerY + playerSize * 0.78f + shakeY),
                size = Size(playerSize * 0.15f, legHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = Color(0xFF2E8A57),
                topLeft = Offset(playerX + playerSize * 0.55f - legOffset, playerY + playerSize * 0.78f + shakeY),
                size = Size(playerSize * 0.15f, legHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )

            if (shieldTimer > 0f) {
                drawCircle(
                    color = Color(0x557DE2FF),
                    radius = playerSize * 0.72f,
                    center = Offset(playerX + playerSize * 0.5f, playerY + playerSize * 0.5f + shakeY),
                )
            }

            obstacles.forEach { obstacle ->
                val obstacleColor = when (obstacle.type) {
                    ObstacleType.BLOCK_LOW -> Color(0xFFFF7A7A)
                    ObstacleType.BLOCK_TALL -> Color(0xFFFF5E5E)
                    ObstacleType.DRONE -> Color(0xFFFFB36B)
                }
                drawRoundRect(
                    color = obstacleColor,
                    topLeft = Offset(obstacle.x + shakeX, obstacle.y + shakeY),
                    size = Size(obstacle.width, obstacle.height),
                    cornerRadius = CornerRadius(8f, 8f),
                )
                if (obstacle.type == ObstacleType.DRONE) {
                    drawCircle(
                        color = Color(0xAA3A1F0E),
                        radius = 2.6f,
                        center = Offset(
                            obstacle.x + obstacle.width * 0.3f + shakeX,
                            obstacle.y + obstacle.height * 0.5f + shakeY,
                        ),
                    )
                    drawCircle(
                        color = Color(0xAA3A1F0E),
                        radius = 2.6f,
                        center = Offset(
                            obstacle.x + obstacle.width * 0.7f + shakeX,
                            obstacle.y + obstacle.height * 0.5f + shakeY,
                        ),
                    )
                }
            }

            powerUps.forEach { item ->
                val color = when (item.type) {
                    PowerUpType.SHIELD -> Color(0xFF7DE2FF)
                    PowerUpType.SLOW_MO -> Color(0xFFFFC857)
                    PowerUpType.DOUBLE_JUMP -> Color(0xFFA1F28A)
                }
                drawCircle(color = color, radius = item.radius, center = Offset(item.x + shakeX, item.y + shakeY))
                when (item.type) {
                    PowerUpType.SHIELD -> {
                        drawCircle(
                            color = Color(0xAA0E1C2F),
                            radius = item.radius * 0.45f,
                            center = Offset(item.x + shakeX, item.y + shakeY),
                        )
                    }
                    PowerUpType.SLOW_MO -> {
                        drawCircle(
                            color = Color(0xAA0E1C2F),
                            radius = item.radius * 0.16f,
                            center = Offset(item.x + shakeX, item.y + shakeY),
                        )
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - 1.3f + shakeX, item.y - item.radius * 0.58f + shakeY),
                            size = Size(2.6f, item.radius * 0.56f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                    PowerUpType.DOUBLE_JUMP -> {
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - item.radius * 0.56f + shakeX, item.y + 2f + shakeY),
                            size = Size(item.radius * 1.12f, 3.2f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - item.radius * 0.26f + shakeX, item.y - item.radius * 0.26f + shakeY),
                            size = Size(item.radius * 0.52f, 3.2f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                }
            }

            particles.forEach { p ->
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = 3f * alpha + 1f,
                    center = Offset(p.x + shakeX, p.y + shakeY),
                )
            }
        }

        val missionScoreDone = dailyProgress.bestScore >= missionScoreTarget
        val missionJumpsDone = dailyProgress.jumps >= missionJumpsTarget
        val missionPowerupsDone = dailyProgress.powerups >= missionPowerupsTarget
        val missionsDone = listOf(missionScoreDone, missionJumpsDone, missionPowerupsDone).count { it }

        Text(
            text = "Score: ${score.toInt()}  Best: ${bestScore.toInt()}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        val activePowerups = buildList {
            if (shieldTimer > 0f) add("Shield ${(shieldTimer + 0.5f).toInt()}s")
            if (slowMoTimer > 0f) add("Slow ${(slowMoTimer + 0.5f).toInt()}s")
            if (doubleJumpTimer > 0f) add("2x Jump ${(doubleJumpTimer + 0.5f).toInt()}s")
        }.joinToString("  |  ")

        if (activePowerups.isNotEmpty() && started && !gameOver) {
            Text(
                text = activePowerups,
                color = Color(0xFFD7F6FF),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (started && !gameOver) {
            Text(
                text = if (isPaused) "▶" else "Ⅱ",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 20.dp)
                    .background(Color(0x44223344), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { isPaused = !isPaused },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!started) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Runner2D\nTap to start",
                    color = Color.White,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Daily missions: $missionsDone/3",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Score $missionScoreTarget (${dailyProgress.bestScore}/$missionScoreTarget)"
                        .let { if (missionScoreDone) "✓ $it" else "• $it" },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                Text(
                    text = "Jumps $missionJumpsTarget (${dailyProgress.jumps}/$missionJumpsTarget)"
                        .let { if (missionJumpsDone) "✓ $it" else "• $it" },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                Text(
                    text = "Power-ups $missionPowerupsTarget (${dailyProgress.powerups}/$missionPowerupsTarget)"
                        .let { if (missionPowerupsDone) "✓ $it" else "• $it" },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                if (tutorialVisible) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0x66212D40), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Quick Tutorial",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "• Tap to jump",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "• Yellow pickup = slow motion",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "• Blue shield absorbs one hit",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Tap anywhere to close tutorial",
                            color = Color(0xFFA1F28A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        } else if (isPaused) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Paused",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Tap ▶ to resume",
                    color = Color.White,
                    fontSize = 18.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Audio ${if (audioEnabled) "ON" else "OFF"}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                if (audioEnabled) Color(0x446FD08C) else Color(0x44FF6B6B),
                                CircleShape,
                            )
                            .clickable {
                                audioEnabled = !audioEnabled
                                prefs.edit().putBoolean("audio_enabled", audioEnabled).apply()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "Haptics ${if (hapticsEnabled) "ON" else "OFF"}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                if (hapticsEnabled) Color(0x446FD08C) else Color(0x44FF6B6B),
                                CircleShape,
                            )
                            .clickable {
                                hapticsEnabled = !hapticsEnabled
                                prefs.edit().putBoolean("haptics_enabled", hapticsEnabled).apply()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = "Daily missions: $missionsDone/3",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
                Text(
                    text = "Show Tutorial",
                    color = Color(0xFFA1F28A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0x33212D40), CircleShape)
                        .clickable {
                            tutorialVisible = true
                            started = false
                            isPaused = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        } else if (gameOver) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x77212D40), shape = CircleShape)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Game Over",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = "Run score: ${score.toInt()}", color = Color.White, fontSize = 16.sp)
                Text(text = "Jumps: $sessionJumps", color = Color.White, fontSize = 14.sp)
                Text(text = "Power-ups: $sessionPowerups", color = Color.White, fontSize = 14.sp)
                Text(
                    text = "Daily missions: $missionsDone/3",
                    color = Color(0xFFD7F6FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!usedSecondChanceThisRun) {
                    Text(
                        text = if (adsManager.isRewardedReady) {
                            "Watch ad for second chance"
                        } else {
                            "Second chance unavailable"
                        },
                        color = if (adsManager.isRewardedReady) Color(0xFFA1F28A) else Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                if (adsManager.isRewardedReady) Color(0x3333AA66) else Color(0x22000000),
                                CircleShape,
                            )
                            .clickable(enabled = adsManager.isRewardedReady && activity != null) {
                                val host = activity ?: return@clickable
                                val shown = adsManager.showRewarded(
                                    activity = host,
                                    onRewardEarned = { continueAfterReward() },
                                    onAdClosed = {},
                                )
                                if (!shown) {
                                    playSfx(Sfx.UI_TAP)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = "Tap to restart",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
        }

        if (showStaticOverlay) {
            RunnerBannerAd(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            frameTicker += 1f
            delay(16)
        }
    }

    LaunchedEffect(gameOver) {
        if (gameOver) {
            playSfx(Sfx.GAME_OVER)
            if (hapticsEnabled) {
                vibrateGameOver(context)
            }
        }
    }
}
