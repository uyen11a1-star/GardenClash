package com.huy.gardenclash

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GardenGameView(context: Context) : View(context) {
    private enum class Screen { HOME, MODE_SELECT, BATTLE, PAUSE, RESULT, COLLECTION, SETTINGS }

    private enum class PlantType(val cost: Int, val color: Int, val accent: Int, val title: String) {
        SUN_BLOOM(75, Color.rgb(255, 211, 78), Color.rgb(255, 243, 171), "Sun Bloom"),
        PEA_POD(100, Color.rgb(89, 188, 89), Color.rgb(190, 244, 123), "Pea Pod"),
        BURST_BERRY(150, Color.rgb(222, 94, 122), Color.rgb(255, 170, 180), "Burst Berry"),
        WALL_BUD(50, Color.rgb(82, 144, 97), Color.rgb(185, 231, 163), "Wall Bud")
    }

    private data class Plant(
        val row: Int,
        val col: Int,
        val type: PlantType,
        var hp: Float = 100f,
        var timer: Float = 0f
    )

    private data class Enemy(
        var row: Int,
        var x: Float,
        val type: Int,
        var hp: Float,
        val maxHp: Float,
        var speed: Float,
        var attackTimer: Float = 0f,
        var bob: Float = 0f
    )

    private data class Bullet(var row: Int, var x: Float, var damage: Float, var kind: Int = 0)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private data class FloatText(var x: Float, var y: Float, var text: String, var life: Float)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val normalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plants = ArrayList<Plant>()
    private val enemies = ArrayList<Enemy>()
    private val bullets = ArrayList<Bullet>()
    private val particles = ArrayList<Particle>()
    private val floatTexts = ArrayList<FloatText>()

    private var lastNs = System.nanoTime()
    private var spawnTimer = 0f
    private var timeAlive = 0f
    private var score = 0
    private var crowns = 0
    private var suns = 250
    private var lives = 3
    private var selected = PlantType.PEA_POD
    private var gameOver = false
    private var victory = false
    private var screen = Screen.HOME
    private var selectedMode = "ADVENTURE"
    private var sfxEnabled = true
    private var musicEnabled = true
    private var vibrationEnabled = true
    private var highGraphics = true
    private var victoryFlash = 0f
    private val rand = Random(77)

    private val rows = 5
    private val cols = 9

    private fun sx(): Float = width / 1280f
    private fun sy(): Float = height / 720f
    private fun X(v: Float): Float = v * sx()
    private fun Y(v: Float): Float = v * sy()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postOnAnimation(gameLoop)
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = min(0.05f, (now - lastNs) / 1_000_000_000f)
            lastNs = now
            update(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun update(dt: Float) {
        if (screen != Screen.BATTLE || gameOver) return
        timeAlive += dt
        spawnTimer += dt
        victoryFlash = max(0f, victoryFlash - dt)

        if (spawnTimer > max(0.95f, 2.5f - timeAlive * 0.015f)) {
            spawnTimer = 0f
            spawnEnemy()
            if (timeAlive > 18f && rand.nextFloat() < 0.22f) spawnEnemy()
        }

        plants.forEach { plant ->
            plant.timer += dt
            when (plant.type) {
                PlantType.SUN_BLOOM -> {
                    if (plant.timer > 5f) {
                        plant.timer = 0f
                        suns += 25
                        sparkle(plant.row, plant.col, PlantType.SUN_BLOOM.accent)
                        popText(cellCenterX(plant.col), cellCenterY(plant.row) - 45f, "+25", PlantType.SUN_BLOOM.accent)
                    }
                }
                PlantType.PEA_POD -> {
                    if (plant.timer > 0.95f) {
                        plant.timer = 0f
                        bullets += Bullet(plant.row, cellCenterX(plant.col) + 25f, 22f)
                    }
                }
                PlantType.BURST_BERRY -> {
                    if (plant.timer > 2.6f) {
                        plant.timer = 0f
                        bullets += Bullet(plant.row, cellCenterX(plant.col) + 25f, 48f, 1)
                    }
                }
                PlantType.WALL_BUD -> Unit
            }
        }

        bullets.forEach { it.x += if (it.kind == 1) 360f * dt else 430f * dt }

        val toRemoveBullets = HashSet<Bullet>()
        bullets.forEach { bullet ->
            enemies.asSequence().filter { it.row == bullet.row && it.x < 980f && abs(it.x - bullet.x) < 38f }.firstOrNull()?.let { enemy ->
                enemy.hp -= bullet.damage
                toRemoveBullets += bullet
                for (i in 0 until 5) particles += Particle(bullet.x, cellCenterY(bullet.row), rand.nextFloat() * 100f - 50f, rand.nextFloat() * 80f - 40f, 0.45f, if (bullet.kind == 1) PlantType.BURST_BERRY.accent else PlantType.PEA_POD.accent)
                if (bullet.kind == 1) {
                    enemies.filter { it.row == bullet.row && abs(it.x - bullet.x) < 110f }.forEach { it.hp -= 22f }
                }
            }
        }
        bullets.removeAll(toRemoveBullets)
        bullets.removeAll { it.x > 1160f }

        val deadEnemies = enemies.filter { it.hp <= 0f }
        deadEnemies.forEach { enemy ->
            score += when (enemy.type) { 1 -> 45; 2 -> 35; else -> 25 }
            suns += when (enemy.type) { 1 -> 15; 2 -> 12; else -> 10 }
            if (score / 250 > crowns) {
                crowns = score / 250
                victoryFlash = 0.8f
                popText(1040f, 82f, "CROWN +1", Color.rgb(255, 225, 110))
            }
            repeat(10) { sparkleAt(enemy.x, cellCenterY(enemy.row), when (enemy.type) { 1 -> Color.rgb(196, 120, 70); 2 -> Color.rgb(210, 90, 150); else -> Color.rgb(125, 214, 103) }) }
        }
        enemies.removeAll(deadEnemies.toSet())

        enemies.forEach { enemy ->
            enemy.bob += dt * 5f
            enemy.attackTimer += dt
            val blocker = plants.firstOrNull { it.row == enemy.row && cellCenterX(it.col) + 25f > enemy.x - 25f && cellCenterX(it.col) - 25f < enemy.x + 25f }
            if (blocker == null) {
                enemy.x -= enemy.speed * dt
            } else if (enemy.attackTimer > 0.55f) {
                enemy.attackTimer = 0f
                blocker.hp -= when (enemy.type) { 1 -> 13f; 2 -> 7f; else -> 9f }
                repeat(3) { sparkleAt(cellCenterX(blocker.col), cellCenterY(blocker.row), Color.rgb(250, 180, 105)) }
            }
        }

        val brokenPlants = plants.filter { it.hp <= 0f }
        brokenPlants.forEach { p -> repeat(8) { sparkle(p.row, p.col, PlantType.WALL_BUD.accent) } }
        plants.removeAll(brokenPlants.toSet())

        enemies.filter { it.x < 100f }.forEach { enemy ->
            lives--
            repeat(10) { sparkleAt(110f, cellCenterY(enemy.row), Color.rgb(255, 224, 95)) }
        }
        enemies.removeAll { it.x < 100f }
        if (lives <= 0) { gameOver = true; victory = false; screen = Screen.RESULT }
        if (timeAlive >= 60f && !gameOver) { gameOver = true; victory = true; screen = Screen.RESULT }

        particles.forEach {
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.vy += 90f * dt
            it.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        floatTexts.forEach { it.y -= 28f * dt; it.life -= dt }
        floatTexts.removeAll { it.life <= 0f }
    }

    private fun spawnEnemy() {
        val roll = rand.nextFloat()
        val type = if (timeAlive > 30f && roll < 0.14f) 2 else if (timeAlive > 25f && roll < 0.32f) 1 else 0
        val row = rand.nextInt(rows)
        val hp = when (type) { 1 -> 170f; 2 -> 55f; else -> 80f }
        val speed = when (type) { 1 -> 29f; 2 -> 64f; else -> 38f }
        enemies += Enemy(row, 1110f, type, hp, hp, speed)
    }

    private fun sparkle(row: Int, col: Int, color: Int) {
        sparkleAt(cellCenterX(col), cellCenterY(row), color)
    }

    private fun sparkleAt(x: Float, y: Float, color: Int) {
        repeat(1) {
            particles += Particle(x, y, rand.nextFloat() * 90f - 45f, rand.nextFloat() * 90f - 45f, 0.55f, color)
        }
    }

    private fun popText(x: Float, y: Float, text: String, color: Int) {
        floatTexts += FloatText(x, y, text, 0.95f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 34, 22))
        when (screen) {
            Screen.HOME -> drawHome(canvas)
            Screen.MODE_SELECT -> drawModeSelect(canvas)
            Screen.BATTLE -> {
                drawBackground(canvas)
                drawTopBar(canvas)
                drawBoard(canvas)
                drawPlants(canvas)
                drawEnemies(canvas)
                drawBullets(canvas)
                drawParticles(canvas)
                drawPlantBar(canvas)
                drawFloatTexts(canvas)
                if (victoryFlash > 0f) {
                    uiPaint.color = Color.argb((victoryFlash * 80).toInt(), 255, 222, 120)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), uiPaint)
                }
            }
            Screen.PAUSE -> { drawBattleScene(canvas); drawPause(canvas) }
            Screen.RESULT -> { drawBattleScene(canvas); drawResult(canvas) }
            Screen.COLLECTION -> drawCollection(canvas)
            Screen.SETTINGS -> drawSettings(canvas)
        }
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int = Color.argb(235, 13, 36, 23)) {
        uiPaint.color = color
        c.drawRoundRect(X(l), Y(t), X(r), Y(b), X(28f), X(28f), uiPaint)
    }

    private fun button(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, enabled: Boolean = true) {
        uiPaint.color = if (enabled) Color.rgb(229, 190, 72) else Color.rgb(80, 91, 78)
        c.drawRoundRect(X(x), Y(y), X(x+w), Y(y+h), X(18f), X(18f), uiPaint)
        textPaint.color = if (enabled) Color.rgb(48, 38, 18) else Color.rgb(190, 196, 187)
        textPaint.textSize = X(23f)
        c.drawText(label, X(x + 26f), Y(y + h*0.65f), textPaint)
    }

    private fun title(c: Canvas, main: String, sub: String) {
        textPaint.color = Color.WHITE; textPaint.textSize = X(58f)
        c.drawText(main, X(70f), Y(110f), textPaint)
        normalTextPaint.color = Color.rgb(198, 224, 196); normalTextPaint.textSize = X(22f)
        c.drawText(sub, X(74f), Y(145f), normalTextPaint)
    }

    private fun drawHome(c: Canvas) {
        drawBackground(c)
        panel(c, 55f, 42f, 1225f, 678f, Color.argb(205, 8, 28, 18))
        title(c, "GARDEN CLASH", "Grow your garden. Hold the line. Earn Crowns.")
        button(c, 80f, 195f, 300f, 68f, "PLAY")
        button(c, 80f, 285f, 300f, 68f, "PLANTS")
        button(c, 80f, 375f, 300f, 68f, "ENEMIES")
        button(c, 80f, 465f, 300f, 68f, "SETTINGS")
        button(c, 410f, 195f, 300f, 68f, "ARENA")
        button(c, 410f, 285f, 300f, 68f, "CHALLENGE", false)
        button(c, 410f, 375f, 300f, 68f, "ENDLESS", false)
        normalTextPaint.color = Color.rgb(235, 241, 211); normalTextPaint.textSize = X(20f)
        c.drawText("60-second Garden Trial", X(410f), Y(485f), normalTextPaint)
        c.drawText("5 lanes · 9 tiles · 4 original defenders", X(410f), Y(520f), normalTextPaint)
        panel(c, 775f, 190f, 1190f, 555f, Color.argb(190, 25, 70, 39))
        textPaint.color = Color.rgb(255, 224, 111); textPaint.textSize = X(30f)
        c.drawText("YOUR GARDEN", X(820f), Y(245f), textPaint)
        PlantType.entries.forEachIndexed { i, p ->
            drawPlantIcon(c, 845f, 305f + i*58f, p)
            normalTextPaint.color = Color.WHITE; normalTextPaint.textSize = X(20f)
            c.drawText(p.title, X(885f), Y(312f + i*58f), normalTextPaint)
            normalTextPaint.color = Color.rgb(255, 221, 100)
            c.drawText("☀ ${p.cost}", X(1055f), Y(312f + i*58f), normalTextPaint)
        }
    }

    private fun drawModeSelect(c: Canvas) {
        drawHome(c)
        panel(c, 250f, 105f, 1030f, 620f)
        title(c, "CHOOSE MODE", "Pick a mode for this build.")
        button(c, 330f, 205f, 300f, 72f, "ADVENTURE")
        button(c, 650f, 205f, 300f, 72f, "ARENA")
        button(c, 330f, 305f, 300f, 72f, "CHALLENGE", false)
        button(c, 650f, 305f, 300f, 72f, "ENDLESS", false)
        button(c, 490f, 500f, 300f, 65f, "BACK")
    }

    private fun drawBattleScene(c: Canvas) {
        drawBackground(c); drawTopBar(c); drawBoard(c); drawPlants(c); drawEnemies(c); drawBullets(c); drawParticles(c); drawPlantBar(c); drawFloatTexts(c)
    }

    private fun drawPause(c: Canvas) {
        uiPaint.color = Color.argb(190, 5, 16, 10); c.drawRect(0f,0f,width.toFloat(),height.toFloat(),uiPaint)
        panel(c, 410f, 145f, 870f, 565f)
        textPaint.color = Color.WHITE; textPaint.textSize = X(48f); c.drawText("PAUSED", X(525f), Y(215f), textPaint)
        button(c, 490f, 260f, 300f, 62f, "RESUME")
        button(c, 490f, 340f, 300f, 62f, "RESTART")
        button(c, 490f, 420f, 300f, 62f, "QUIT")
    }

    private fun drawResult(c: Canvas) {
        uiPaint.color = Color.argb(210, 7, 20, 12); c.drawRect(0f,0f,width.toFloat(),height.toFloat(),uiPaint)
        panel(c, 300f, 105f, 980f, 625f)
        textPaint.color = if (victory) Color.rgb(255,224,105) else Color.rgb(255,130,120)
        textPaint.textSize = X(50f)
        c.drawText(if (victory) "GARDEN SECURED!" else "GARDEN FALLEN", X(425f), Y(190f), textPaint)
        normalTextPaint.color = Color.WHITE; normalTextPaint.textSize = X(25f)
        c.drawText("Score: $score", X(475f), Y(250f), normalTextPaint)
        c.drawText("Crowns: $crowns", X(475f), Y(290f), normalTextPaint)
        c.drawText("Survived: ${timeAlive.toInt()}s", X(475f), Y(330f), normalTextPaint)
        button(c, 425f, 390f, 230f, 62f, "PLAY AGAIN")
        button(c, 675f, 390f, 190f, 62f, "HOME")
    }

    private fun drawCollection(c: Canvas) {
        drawHome(c); panel(c, 300f, 85f, 1000f, 650f)
        title(c, "COLLECTION", "Your current defenders and threats.")
        PlantType.entries.forEachIndexed { i,p ->
            val x = 360f + (i%2)*300f; val y = 205f + (i/2)*180f
            drawPlantIcon(c,x,y,p); textPaint.color=Color.WHITE; textPaint.textSize=X(23f)
            c.drawText(p.title,X(x+45f),Y(y+5f),textPaint)
            normalTextPaint.color=Color.rgb(205,225,203); normalTextPaint.textSize=X(17f)
            c.drawText("Cost ${p.cost}",X(x+45f),Y(y+32f),normalTextPaint)
        }
        button(c, 540f, 570f, 220f, 58f, "BACK")
    }

    private fun drawSettings(c: Canvas) {
        drawHome(c); panel(c, 300f, 85f, 1000f, 650f)
        title(c, "SETTINGS", "Gameplay options are saved for this session.")
        val labels=listOf("SFX","MUSIC","VIBRATION","HIGH GRAPHICS")
        val values=listOf(sfxEnabled,musicEnabled,vibrationEnabled,highGraphics)
        labels.forEachIndexed { i,l ->
            val y=215f+i*72f
            textPaint.color=Color.WHITE;textPaint.textSize=X(24f);c.drawText(l,X(390f),Y(y),textPaint)
            button(c,700f,y-34f,190f,52f,if(values[i])"ON" else "OFF")
        }
        button(c, 540f, 535f, 220f, 58f, "BACK")
    }

    private fun drawBackground(c: Canvas) {
        val sky = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(137, 202, 239), Color.rgb(222, 239, 176), Shader.TileMode.CLAMP)
        bgPaint.shader = sky
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null
        bgPaint.color = Color.argb(85, 255, 255, 255)
        c.drawCircle(X(1080f), Y(115f), X(48f), bgPaint)

        // distant hills
        val hill = Path().apply {
            moveTo(0f, Y(330f)); cubicTo(X(220f), Y(225f), X(430f), Y(395f), X(640f), Y(300f))
            cubicTo(X(820f), Y(215f), X(1010f), Y(390f), X(1280f), Y(260f));
            lineTo(X(1280f), Y(410f)); lineTo(0f, Y(410f)); close()
        }
        bgPaint.color = Color.rgb(103, 172, 95)
        c.drawPath(hill, bgPaint)
        bgPaint.color = Color.rgb(74, 138, 75)
        c.drawRect(0f, Y(375f), width.toFloat(), height.toFloat(), bgPaint)

        for (i in 0 until 18) {
            val x = (i * 82 + 35).toFloat()
            val y = 325f + (i % 3) * 12f
            bgPaint.color = if (i % 2 == 0) Color.rgb(55, 124, 66) else Color.rgb(63, 139, 72)
            c.drawCircle(X(x), Y(y), X(15f), bgPaint)
            c.drawRect(X(x - 2), Y(y + 10), X(x + 2), Y(y + 40), bgPaint)
        }
    }

    private fun drawTopBar(c: Canvas) {
        uiPaint.color = Color.argb(238, 13, 36, 23)
        c.drawRoundRect(X(26f), Y(20f), X(1254f), Y(103f), X(25f), X(25f), uiPaint)

        textPaint.textSize = X(30f); textPaint.color = Color.WHITE
        c.drawText("GARDEN CLASH", X(54f), Y(58f), textPaint)
        normalTextPaint.textSize = X(18f); normalTextPaint.color = Color.rgb(198, 220, 198)
        c.drawText("Arena Garden · Solo Trial", X(56f), Y(82f), normalTextPaint)

        badge(c, 332f, 35f, 180f, 54f, Color.rgb(37, 95, 57), "SOIL LEAGUE", Color.rgb(209, 241, 209))
        badge(c, 530f, 35f, 150f, 54f, Color.rgb(95, 69, 25), "♛  "+crowns, Color.rgb(255, 224, 120))
        badge(c, 698f, 35f, 180f, 54f, Color.rgb(25, 75, 45), "☀  "+suns, Color.rgb(255, 222, 100))
        badge(c, 896f, 35f, 180f, 54f, Color.rgb(25, 75, 45), "SCORE  "+score, Color.WHITE)

        for (i in 0 until 3) {
            uiPaint.color = if (i < lives) Color.rgb(238, 105, 89) else Color.argb(80, 238, 105, 89)
            c.drawCircle(X(1110f + i * 42f), Y(62f), X(12f), uiPaint)
        }
    }

    private fun badge(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, text: String, txtColor: Int) {
        uiPaint.color = color
        c.drawRoundRect(X(x), Y(y), X(x + w), Y(y + h), X(18f), X(18f), uiPaint)
        textPaint.color = txtColor; textPaint.textSize = X(19f)
        c.drawText(text, X(x + 16f), Y(y + 34f), textPaint)
    }

    private fun drawBoard(c: Canvas) {
        val left = 230f; val top = 150f; val cellW = 93f; val cellH = 82f
        uiPaint.color = Color.argb(215, 41, 119, 65)
        c.drawRoundRect(X(187f), Y(132f), X(1100f), Y(604f), X(34f), X(34f), uiPaint)
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                val x = left + col * cellW
                val y = top + r * cellH
                uiPaint.color = if ((r + col) % 2 == 0) Color.rgb(104, 181, 91) else Color.rgb(95, 171, 84)
                c.drawRoundRect(X(x), Y(y), X(x + cellW - 4f), Y(y + cellH - 4f), X(12f), X(12f), uiPaint)
                uiPaint.color = Color.argb(35, 255, 255, 255)
                c.drawLine(X(x + 10f), Y(y + 12f), X(x + 55f), Y(y + 3f), uiPaint)
            }
        }
        // left gate / mower lane
        uiPaint.color = Color.rgb(238, 207, 116)
        c.drawRoundRect(X(130f), Y(167f), X(184f), Y(570f), X(18f), X(18f), uiPaint)
        textPaint.color = Color.rgb(87, 64, 26); textPaint.textSize = X(18f)
        c.drawText("DEFENSE", X(103f), Y(614f), textPaint)
    }

    private fun drawPlantBar(c: Canvas) {
        val types = PlantType.entries
        val x0 = 26f; val y = 120f
        types.forEachIndexed { i, type ->
            val x = x0 + i * 148f
            uiPaint.color = if (selected == type) Color.rgb(248, 235, 168) else Color.argb(225, 16, 46, 29)
            c.drawRoundRect(X(x), Y(y), X(x + 136f), Y(y + 96f), X(20f), X(20f), uiPaint)
            drawPlantIcon(c, x + 42f, y + 48f, type)
            textPaint.color = if (selected == type) Color.rgb(31, 71, 39) else Color.WHITE
            textPaint.textSize = X(16f)
            c.drawText(type.title, X(x + 64f), Y(y + 37f), textPaint)
            normalTextPaint.color = if (suns >= type.cost) Color.rgb(255, 224, 95) else Color.rgb(255, 135, 127)
            normalTextPaint.textSize = X(14f)
            c.drawText("☀ ${type.cost}", X(x + 64f), Y(y + 63f), normalTextPaint)
        }
    }

    private fun drawPlantIcon(c: Canvas, x: Float, y: Float, type: PlantType) {
        when (type) {
            PlantType.SUN_BLOOM -> {
                uiPaint.color = Color.rgb(82, 149, 73); c.drawRect(X(x - 4f), Y(y + 8f), X(x + 4f), Y(y + 27f), uiPaint)
                repeat(8) { i ->
                    val a = Math.PI * 2 * i / 8.0
                    uiPaint.color = Color.rgb(255, 205, 55)
                    c.drawCircle(X(x + (kotlin.math.cos(a) * 16).toFloat()), Y(y + (kotlin.math.sin(a) * 16).toFloat()), X(8f), uiPaint)
                }
                uiPaint.color = Color.rgb(121, 88, 45); c.drawCircle(X(x), Y(y), X(8f), uiPaint)
            }
            PlantType.PEA_POD -> {
                uiPaint.color = Color.rgb(63, 150, 77); c.drawOval(X(x - 22f), Y(y - 15f), X(x + 20f), Y(y + 18f), uiPaint)
                uiPaint.color = type.accent; c.drawCircle(X(x - 5f), Y(y + 2f), X(11f), uiPaint); c.drawCircle(X(x + 9f), Y(y + 1f), X(11f), uiPaint)
                uiPaint.color = Color.WHITE; c.drawCircle(X(x - 8f), Y(y - 5f), X(3f), uiPaint); c.drawCircle(X(x + 7f), Y(y - 5f), X(3f), uiPaint)
            }
            PlantType.BURST_BERRY -> {
                uiPaint.color = Color.rgb(49, 130, 71); c.drawRect(X(x - 3f), Y(y + 2f), X(x + 3f), Y(y + 22f), uiPaint)
                uiPaint.color = type.color; c.drawCircle(X(x), Y(y - 2f), X(18f), uiPaint)
                uiPaint.color = Color.WHITE; c.drawCircle(X(x - 6f), Y(y - 6f), X(3f), uiPaint); c.drawCircle(X(x + 6f), Y(y - 6f), X(3f), uiPaint)
            }
            PlantType.WALL_BUD -> {
                uiPaint.color = type.color; c.drawOval(X(x - 18f), Y(y - 25f), X(x + 18f), Y(y + 25f), uiPaint)
                uiPaint.color = type.accent; c.drawArc(X(x - 14f), Y(y - 13f), X(x + 14f), Y(y + 20f), 220f, 100f, true, uiPaint)
                uiPaint.color = Color.WHITE; c.drawCircle(X(x - 6f), Y(y - 4f), X(3f), uiPaint); c.drawCircle(X(x + 6f), Y(y - 4f), X(3f), uiPaint)
            }
        }
    }

    private fun drawPlants(c: Canvas) {
        plants.forEach { p ->
            val cx = cellCenterX(p.col); val cy = cellCenterY(p.row)
            uiPaint.color = Color.argb(75, 22, 54, 25)
            c.drawOval(X(cx - 27f), Y(cy + 25f), X(cx + 27f), Y(cy + 37f), uiPaint)
            drawPlantIcon(c, cx, cy + kotlin.math.sin(timeAlive * 2.3 + p.row + p.col).toFloat() * 3f, p.type)
            uiPaint.color = Color.argb(180, 20, 45, 23)
            c.drawRoundRect(X(cx - 28f), Y(cy - 38f), X(cx + 28f), Y(cy - 31f), X(4f), X(4f), uiPaint)
            uiPaint.color = Color.rgb(104, 221, 119)
            c.drawRoundRect(X(cx - 28f), Y(cy - 38f), X(cx - 28f + 56f * (p.hp / 100f).coerceIn(0f, 1f)), Y(cy - 31f), X(4f), X(4f), uiPaint)
        }
    }

    private fun drawEnemies(c: Canvas) {
        enemies.forEach { e ->
            val y = cellCenterY(e.row) + kotlin.math.sin(e.bob) * 3f
            uiPaint.color = Color.argb(70, 22, 40, 20)
            c.drawOval(X(e.x - 28f), Y(y + 26f), X(e.x + 28f), Y(y + 38f), uiPaint)
            when (e.type) { 0 -> drawBasicEnemy(c, e.x, y); 1 -> drawBruteEnemy(c, e.x, y); else -> drawRunnerEnemy(c, e.x, y) }
            uiPaint.color = Color.argb(180, 20, 45, 23)
            c.drawRoundRect(X(e.x - 30f), Y(y - 42f), X(e.x + 30f), Y(y - 35f), X(3f), X(3f), uiPaint)
            uiPaint.color = Color.rgb(228, 100, 90)
            c.drawRoundRect(X(e.x - 30f), Y(y - 42f), X(e.x - 30f + 60f * (e.hp / e.maxHp).coerceIn(0f, 1f)), Y(y - 35f), X(3f), X(3f), uiPaint)
        }
    }

    private fun drawBasicEnemy(c: Canvas, x: Float, y: Float) {
        uiPaint.color = Color.rgb(114, 92, 113); c.drawOval(X(x - 20f), Y(y - 25f), X(x + 22f), Y(y + 28f), uiPaint)
        uiPaint.color = Color.rgb(224, 218, 220); c.drawOval(X(x - 15f), Y(y - 16f), X(x + 15f), Y(y + 18f), uiPaint)
        uiPaint.color = Color.rgb(40, 28, 42); c.drawCircle(X(x - 7f), Y(y - 6f), X(3.2f), uiPaint); c.drawCircle(X(x + 7f), Y(y - 6f), X(3.2f), uiPaint)
        uiPaint.color = Color.rgb(105, 74, 92); c.drawRoundRect(X(x - 8f), Y(y + 5f), X(x + 9f), Y(y + 9f), X(2f), X(2f), uiPaint)
        uiPaint.color = Color.rgb(71, 61, 93); c.drawRect(X(x - 18f), Y(y + 21f), X(x - 6f), Y(y + 38f), uiPaint); c.drawRect(X(x + 6f), Y(y + 21f), X(x + 18f), Y(y + 38f), uiPaint)
    }

    private fun drawBruteEnemy(c: Canvas, x: Float, y: Float) {
        uiPaint.color = Color.rgb(106, 79, 65); c.drawOval(X(x - 28f), Y(y - 32f), X(x + 28f), Y(y + 30f), uiPaint)
        uiPaint.color = Color.rgb(182, 146, 106); c.drawOval(X(x - 18f), Y(y - 16f), X(x + 18f), Y(y + 20f), uiPaint)
        uiPaint.color = Color.rgb(54, 38, 32); c.drawCircle(X(x - 8f), Y(y - 4f), X(4f), uiPaint); c.drawCircle(X(x + 8f), Y(y - 4f), X(4f), uiPaint)
        uiPaint.color = Color.rgb(74, 57, 44); c.drawRoundRect(X(x - 12f), Y(y + 6f), X(x + 12f), Y(y + 12f), X(2f), X(2f), uiPaint)
        uiPaint.color = Color.rgb(65, 54, 46); c.drawRect(X(x - 24f), Y(y + 23f), X(x - 7f), Y(y + 48f), uiPaint); c.drawRect(X(x + 7f), Y(y + 23f), X(x + 24f), Y(y + 48f), uiPaint)
        uiPaint.color = Color.rgb(195, 196, 205); c.drawRoundRect(X(x - 34f), Y(y - 28f), X(x - 20f), Y(y - 5f), X(4f), X(4f), uiPaint)
    }

    private fun drawRunnerEnemy(c: Canvas, x: Float, y: Float) {
        uiPaint.color = Color.rgb(171, 72, 132); c.drawOval(X(x-20f),Y(y-28f),X(x+20f),Y(y+25f),uiPaint)
        uiPaint.color = Color.rgb(242, 181, 211); c.drawOval(X(x-14f),Y(y-17f),X(x+14f),Y(y+16f),uiPaint)
        uiPaint.color = Color.rgb(55,25,50); c.drawCircle(X(x-6f),Y(y-5f),X(3f),uiPaint); c.drawCircle(X(x+7f),Y(y-5f),X(3f),uiPaint)
        uiPaint.color = Color.rgb(76,45,70); c.drawRect(X(x-17f),Y(y+20f),X(x-4f),Y(y+38f),uiPaint); c.drawRect(X(x+4f),Y(y+20f),X(x+17f),Y(y+38f),uiPaint)
    }

    private fun drawBullets(c: Canvas) {
        bullets.forEach { b ->
            val y = cellCenterY(b.row)
            uiPaint.color = if (b.kind == 1) Color.rgb(255, 120, 150) else Color.rgb(170, 240, 100)
            c.drawCircle(X(b.x), Y(y), X(if (b.kind == 1) 11f else 7f), uiPaint)
            uiPaint.color = Color.argb(110, 255, 255, 255)
            c.drawCircle(X(b.x - 3f), Y(y - 3f), X(3f), uiPaint)
        }
    }

    private fun drawParticles(c: Canvas) {
        particles.forEach { p ->
            uiPaint.color = p.color
            uiPaint.alpha = (255 * (p.life / 0.55f).coerceIn(0f, 1f)).toInt()
            c.drawCircle(X(p.x), Y(p.y), X(3.5f), uiPaint)
            uiPaint.alpha = 255
        }
    }

    private fun drawFloatTexts(c: Canvas) {
        normalTextPaint.textSize = X(18f)
        floatTexts.forEach { f ->
            normalTextPaint.color = if (f.text.startsWith("+")) Color.rgb(236, 255, 196) else Color.rgb(255, 232, 128)
            c.drawText(f.text, X(f.x), Y(f.y), normalTextPaint)
        }
    }

    private fun drawGameOver(c: Canvas) {
        uiPaint.color = Color.argb(210, 9, 22, 15)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), uiPaint)
        textPaint.color = Color.WHITE; textPaint.textSize = X(54f)
        c.drawText("KHU VƯỜN THẤT THỦ", X(350f), Y(305f), textPaint)
        normalTextPaint.color = Color.rgb(225, 244, 218); normalTextPaint.textSize = X(24f)
        c.drawText("Điểm: $score   ·   Crown: $crowns", X(450f), Y(354f), normalTextPaint)
        uiPaint.color = Color.rgb(230, 190, 73)
        c.drawRoundRect(X(492f), Y(392f), X(788f), Y(465f), X(24f), X(24f), uiPaint)
        textPaint.color = Color.rgb(54, 40, 18); textPaint.textSize = X(24f)
        c.drawText("CHƠI LẠI", X(572f), Y(437f), textPaint)
    }

    private fun cellCenterX(col: Int): Float = 230f + col * 93f + 44f
    private fun cellCenterY(row: Int): Float = 150f + row * 82f + 37f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x / sx(); val y = event.y / sy()
        when (screen) {
            Screen.HOME -> {
                when {
                    x in 80f..380f && y in 195f..263f -> screen = Screen.MODE_SELECT
                    x in 80f..380f && y in 285f..353f -> screen = Screen.COLLECTION
                    x in 80f..380f && y in 375f..443f -> screen = Screen.COLLECTION
                    x in 80f..380f && y in 465f..533f -> screen = Screen.SETTINGS
                    x in 410f..710f && y in 195f..263f -> { selectedMode="ARENA"; resetGame(); screen=Screen.BATTLE }
                }
            }
            Screen.MODE_SELECT -> {
                when {
                    x in 330f..630f && y in 205f..277f -> { selectedMode="ADVENTURE"; resetGame(); screen=Screen.BATTLE }
                    x in 650f..950f && y in 205f..277f -> { selectedMode="ARENA"; resetGame(); screen=Screen.BATTLE }
                    x in 490f..790f && y in 500f..565f -> screen=Screen.HOME
                }
            }
            Screen.BATTLE -> {
                if (x > 1120f && y < 110f) { screen=Screen.PAUSE; return true }
                if (y in 120f..216f) {
                    val index=((x-26f)/148f).toInt()
                    if(index in PlantType.entries.indices) selected=PlantType.entries[index]
                    return true
                }
                val col=((x-230f)/93f).toInt(); val row=((y-150f)/82f).toInt()
                if(col !in 0 until cols || row !in 0 until rows) return true
                if(plants.any{it.row==row&&it.col==col}) return true
                if(suns>=selected.cost){ suns-=selected.cost; val hp=if(selected==PlantType.WALL_BUD)180f else 100f; plants+=Plant(row,col,selected,hp); sparkle(row,col,selected.accent) }
            }
            Screen.PAUSE -> {
                when {
                    x in 490f..790f && y in 260f..322f -> screen=Screen.BATTLE
                    x in 490f..790f && y in 340f..402f -> { resetGame(); screen=Screen.BATTLE }
                    x in 490f..790f && y in 420f..482f -> screen=Screen.HOME
                }
            }
            Screen.RESULT -> {
                when {
                    x in 425f..655f && y in 390f..452f -> { resetGame(); screen=Screen.BATTLE }
                    x in 675f..865f && y in 390f..452f -> screen=Screen.HOME
                }
            }
            Screen.COLLECTION -> if (x in 540f..760f && y in 570f..628f) screen=Screen.HOME
            Screen.SETTINGS -> {
                when {
                    x in 700f..890f && y in 181f..233f -> sfxEnabled=!sfxEnabled
                    x in 700f..890f && y in 253f..305f -> musicEnabled=!musicEnabled
                    x in 700f..890f && y in 325f..377f -> vibrationEnabled=!vibrationEnabled
                    x in 700f..890f && y in 397f..449f -> highGraphics=!highGraphics
                    x in 540f..760f && y in 535f..593f -> screen=Screen.HOME
                }
            }
        }
        return true
    }

    private fun resetGame() {
        plants.clear(); enemies.clear(); bullets.clear(); particles.clear(); floatTexts.clear()
        timeAlive = 0f; spawnTimer = 0f; score = 0; crowns = 0; suns = 250; lives = 3; selected = PlantType.PEA_POD; gameOver = false; victory = false
        lastNs = System.nanoTime()
    }
}
