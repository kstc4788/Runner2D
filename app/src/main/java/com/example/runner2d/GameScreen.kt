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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.random.Random

data class Obstacle(
    var x: Float,
    val width: Float,
    val height: Float,
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

    val gravity = 2400f
    val jumpImpulse = -980f
    val baseObstacleSpeed = 420f

    val obstacles = remember { mutableStateListOf<Obstacle>() }
    var spawnTimer by remember { mutableFloatStateOf(0f) }
    var nextSpawnIn by remember { mutableFloatStateOf(1.15f) }

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
        spawnTimer = 0f
        nextSpawnIn = 1.15f
        score = 0f
        gameOver = false
        isPaused = false
        started = true
        playerVelocity = jumpImpulse * 0.6f
        farLayerOffset = 0f
        nearLayerOffset = 0f
        cloudOffset = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1C2F))
            .pointerInput(gameOver, started, isPaused) {
                detectTapGestures {
                    if (!started || gameOver) {
                        resetGame()
                    } else if (!isPaused) {
                        val floorY = canvasHeight - groundHeightPx - playerSize
                        if (playerY >= floorY - 1f) {
                            playerVelocity = jumpImpulse
                            playTone(ToneGenerator.TONE_PROP_BEEP2, 70)
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
            val obstacleSpeed = baseObstacleSpeed * difficulty

            if (started && !gameOver && !isPaused) {
                val dt = 1f / 60f

                playerVelocity += gravity * dt
                playerY += playerVelocity * dt

                if (playerY > floorY) {
                    playerY = floorY
                    playerVelocity = 0f
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

                obstacles.forEach { it.x -= obstacleSpeed * dt }
                obstacles.removeAll { it.x + it.width < -20f }

                val playerRect = Rect(
                    left = size.width * 0.2f,
                    top = playerY,
                    right = size.width * 0.2f + playerSize,
                    bottom = playerY + playerSize,
                )

                for (obstacle in obstacles) {
                    val obstacleRect = Rect(
                        left = obstacle.x,
                        top = size.height - groundHeightPx - obstacle.height,
                        right = obstacle.x + obstacle.width,
                        bottom = size.height - groundHeightPx,
                    )
                    if (playerRect.overlaps(obstacleRect)) {
                        gameOver = true
                        bestScore = max(bestScore, score)
                        prefs.edit().putFloat("best_score", bestScore).apply()
                        break
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

            drawRect(
                color = Color(0xFF6FD08C),
                topLeft = Offset(size.width * 0.2f, playerY),
                size = Size(playerSize, playerSize),
            )
            drawCircle(
                color = Color(0xFF0E1C2F),
                radius = 3.5f,
                center = Offset(size.width * 0.2f + playerSize * 0.72f, playerY + playerSize * 0.32f),
            )

            obstacles.forEach { obstacle ->
                drawRect(
                    color = Color(0xFFFF6B6B),
                    topLeft = Offset(obstacle.x, size.height - groundHeightPx - obstacle.height),
                    size = Size(obstacle.width, obstacle.height),
                )
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
                    text = "Audio: ${if (audioEnabled) "ON" else "OFF"}  |  Haptics: ${if (hapticsEnabled) "ON" else "OFF"}",
                    color = Color.White.copy(alpha = 0.8f),
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
