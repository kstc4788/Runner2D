package com.example.runner2d

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
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Obstacle(
    var x: Float,
    val width: Float,
    val height: Float,
)

enum class PowerUpType {
    SHIELD,
    SLOW_MO,
    DOUBLE_JUMP,
}

data class PowerUpItem(
    var x: Float,
    val y: Float,
    val radius: Float,
    val type: PowerUpType,
)

private fun vibrateGameOver(context: Context) {
    val durationMs = 120L
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(durationMs)
        }
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
    val prefs = remember { context.getSharedPreferences("runner2d_prefs", Context.MODE_PRIVATE) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }

    var audioEnabled by remember { mutableStateOf(prefs.getBoolean("audio_enabled", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("haptics_enabled", true)) }

    fun playTone(toneType: Int, durationMs: Int) {
        if (audioEnabled) tone.startTone(toneType, durationMs)
    }

    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }

    val groundHeightPx = with(LocalDensity.current) { 100.dp.toPx() }
    val playerSize = with(LocalDensity.current) { 48.dp.toPx() }

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    var playerY by remember { mutableFloatStateOf(0f) }
    var playerVelocity by remember { mutableFloatStateOf(0f) }
    var airJumpsUsed by remember { mutableIntStateOf(0) }
    var runningPhase by remember { mutableFloatStateOf(0f) }

    val gravity = 2400f
    val jumpImpulse = -980f
    val baseObstacleSpeed = 420f

    val obstacles = remember { mutableStateListOf<Obstacle>() }
    var spawnTimer by remember { mutableFloatStateOf(0f) }
    var nextSpawnIn by remember { mutableFloatStateOf(1.15f) }

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

    fun resetGame() {
        obstacles.clear()
        powerUps.clear()
        spawnTimer = 0f
        nextSpawnIn = 1.15f
        powerSpawnTimer = 0f
        nextPowerSpawnIn = 6.0f
        score = 0f
        gameOver = false
        isPaused = false
        started = true
        playerVelocity = jumpImpulse * 0.6f
        airJumpsUsed = 0
        runningPhase = 0f
        farLayerOffset = 0f
        nearLayerOffset = 0f
        cloudOffset = 0f
        shieldTimer = 0f
        slowMoTimer = 0f
        doubleJumpTimer = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1C2F))
            .pointerInput(gameOver, started, isPaused, doubleJumpTimer, airJumpsUsed) {
                detectTapGestures {
                    if (!started || gameOver) {
                        resetGame()
                    } else if (!isPaused) {
                        val floorY = canvasHeight - groundHeightPx - playerSize
                        val onGround = playerY >= floorY - 1f
                        if (onGround) {
                            playerVelocity = jumpImpulse
                            airJumpsUsed = 0
                            playTone(ToneGenerator.TONE_PROP_BEEP2, 70)
                        } else if (doubleJumpTimer > 0f && airJumpsUsed < 1) {
                            playerVelocity = jumpImpulse * 0.92f
                            airJumpsUsed += 1
                            playTone(ToneGenerator.TONE_PROP_BEEP, 60)
                        }
                    }
                }
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

            val difficulty = 1f + (score / 180f).coerceAtMost(0.95f)
            val slowMoFactor = if (slowMoTimer > 0f) 0.65f else 1f
            val obstacleSpeed = baseObstacleSpeed * difficulty * slowMoFactor

            if (started && !gameOver && !isPaused) {
                val dt = 1f / 60f

                if (shieldTimer > 0f) shieldTimer = max(0f, shieldTimer - dt)
                if (slowMoTimer > 0f) slowMoTimer = max(0f, slowMoTimer - dt)
                if (doubleJumpTimer > 0f) doubleJumpTimer = max(0f, doubleJumpTimer - dt)

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

                spawnTimer += dt
                if (spawnTimer >= nextSpawnIn) {
                    spawnTimer = 0f
                    val h = Random.nextFloat() * 75f + 55f
                    val w = Random.nextFloat() * 28f + 38f
                    obstacles.add(Obstacle(size.width + 20f, w, h))
                    val jitter = Random.nextFloat() * 0.32f
                    nextSpawnIn = (1.10f - (score / 280f).coerceAtMost(0.45f) + jitter).coerceAtLeast(0.45f)
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
                        playTone(ToneGenerator.TONE_PROP_ACK, 70)
                        collected.add(item)
                    }
                }
                powerUps.removeAll(collected)

                var hitObstacle: Obstacle? = null
                for (obstacle in obstacles) {
                    val obstacleRect = Rect(
                        left = obstacle.x,
                        top = size.height - groundHeightPx - obstacle.height,
                        right = obstacle.x + obstacle.width,
                        bottom = size.height - groundHeightPx,
                    )
                    if (playerRect.overlaps(obstacleRect)) {
                        hitObstacle = obstacle
                        break
                    }
                }

                if (hitObstacle != null) {
                    if (shieldTimer > 0f) {
                        shieldTimer = 0f
                        obstacles.remove(hitObstacle)
                        playTone(ToneGenerator.TONE_PROP_NACK, 80)
                    } else {
                        gameOver = true
                        bestScore = max(bestScore, score)
                        prefs.edit().putFloat("best_score", bestScore).apply()
                    }
                }

                score += dt * 10f * (1f + (difficulty - 1f) * 0.4f)
            }

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

            val playerX = size.width * 0.2f
            val bodyY = playerY + playerSize * 0.25f
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
                center = Offset(playerX + playerSize * 0.52f, playerY + playerSize * 0.21f),
            )
            drawCircle(
                color = Color(0xFF0E1C2F),
                radius = 3.4f,
                center = Offset(playerX + playerSize * 0.59f, playerY + playerSize * 0.20f),
            )
            drawRoundRect(
                color = Color(0xFF2E8A57),
                topLeft = Offset(playerX + playerSize * 0.23f + legOffset, playerY + playerSize * 0.78f),
                size = Size(playerSize * 0.15f, legHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = Color(0xFF2E8A57),
                topLeft = Offset(playerX + playerSize * 0.55f - legOffset, playerY + playerSize * 0.78f),
                size = Size(playerSize * 0.15f, legHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )

            if (shieldTimer > 0f) {
                drawCircle(
                    color = Color(0x557DE2FF),
                    radius = playerSize * 0.72f,
                    center = Offset(playerX + playerSize * 0.5f, playerY + playerSize * 0.5f),
                )
            }

            obstacles.forEach { obstacle ->
                drawRoundRect(
                    color = Color(0xFFFF6B6B),
                    topLeft = Offset(obstacle.x, size.height - groundHeightPx - obstacle.height),
                    size = Size(obstacle.width, obstacle.height),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }

            powerUps.forEach { item ->
                val color = when (item.type) {
                    PowerUpType.SHIELD -> Color(0xFF7DE2FF)
                    PowerUpType.SLOW_MO -> Color(0xFFFFC857)
                    PowerUpType.DOUBLE_JUMP -> Color(0xFFA1F28A)
                }
                drawCircle(color = color, radius = item.radius, center = Offset(item.x, item.y))
                when (item.type) {
                    PowerUpType.SHIELD -> {
                        drawCircle(
                            color = Color(0xAA0E1C2F),
                            radius = item.radius * 0.45f,
                            center = Offset(item.x, item.y),
                        )
                    }
                    PowerUpType.SLOW_MO -> {
                        drawCircle(
                            color = Color(0xAA0E1C2F),
                            radius = item.radius * 0.16f,
                            center = Offset(item.x, item.y),
                        )
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - 1.3f, item.y - item.radius * 0.58f),
                            size = Size(2.6f, item.radius * 0.56f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                    PowerUpType.DOUBLE_JUMP -> {
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - item.radius * 0.56f, item.y + 2f),
                            size = Size(item.radius * 1.12f, 3.2f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                        drawRoundRect(
                            color = Color(0xAA0E1C2F),
                            topLeft = Offset(item.x - item.radius * 0.26f, item.y - item.radius * 0.26f),
                            size = Size(item.radius * 0.52f, 3.2f),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                }
            }
        }

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
                    text = "Power-ups: Shield / Slow / Double Jump",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
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
            }
        } else if (gameOver) {
            Text(
                text = "Game Over\nTap to restart",
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
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
            playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
            if (hapticsEnabled) {
                vibrateGameOver(context)
            }
        }
    }
}
