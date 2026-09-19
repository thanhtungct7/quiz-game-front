package com.kma.quiz_game.ui.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.kma.quiz_game.ui.game.art.AttackStyle
import com.kma.quiz_game.ui.game.art.BattleArt
import com.kma.quiz_game.ui.game.art.BattleSprites
import com.kma.quiz_game.ui.game.art.HeroArt
import com.kma.quiz_game.ui.game.art.HeroClip
import kotlin.math.max
import kotlin.math.min

/**
 * The battle arena: everything on screen that moves at sixty frames a second.
 *
 * Deliberately *not* the whole battle screen. The question, the option buttons, the health bars and
 * the skill bar are Compose, drawn on top of this surface -- they are text and controls, and
 * rebuilding them here would cost accessibility, text shaping and theming for nothing. What lives
 * here is what Compose is bad at: a wind-up ring that has to be smooth, a fighter that squashes
 * when it is hit, and damage numbers that float and fade.
 *
 * Two fighters, a left and a right, and one renderer for both modes. In a lesson battle the right
 * one is a monster; in a duo match it is a second knight, drawn from the same sprite page flipped
 * to face the first and tinted so the two never read as one. Everything under that -- the lunges,
 * the flinches, the floaters, the shake -- was already symmetric and is shared as it stands.
 *
 * The backdrop is drawn here rather than by Compose behind this surface, and that is not a
 * preference. A GL surface is a separate layer the window punches a hole for; anything Compose
 * painted behind it would be composited *over* it, not under. So the stage belongs to the arena.
 *
 * Sprites come from [BattleSprites], which answers null when its pages are missing -- in which case
 * every draw here falls back to the shapes this screen shipped with, and the fight goes on.
 */
class BattleArena(private val bridge: ArenaBridge) : ApplicationAdapter() {

    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var camera: OrthographicCamera
    private var sprites: BattleSprites? = null

    private val floaters = ArrayList<Floater>()
    private val tint = Color()

    /** Seconds left of each fighter's flinch, and of the screen shake a blow causes. */
    private var rightFlinch = 0f
    private var leftFlinch = 0f
    private var shake = 0f
    /** Seconds left of the stagger a wrong answer causes, per side. */
    private var leftStagger = 0f
    private var rightStagger = 0f
    /** Runs forever; drives the idle bob so a still fight is not a still picture. */
    private var clock = 0f

    /** What each knight is doing, and how far into doing it he is. */
    private var leftClip = HeroClip.IDLE
    private var leftElapsed = 0f
    private var rightClip = HeroClip.IDLE
    private var rightElapsed = 0f

    /** The blow currently flying, if any, plus whatever landed while one was already in flight. */
    private var leftLunge: Lunge? = null
    private var rightLunge: Lunge? = null
    private val pendingLeft = ArrayDeque<Lunge>()
    private val pendingRight = ArrayDeque<Lunge>()

    override fun create() {
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        // The font bundled inside gdx.jar: damage numbers are the one thing here that is text.
        font = BitmapFont()
        camera = OrthographicCamera()
        sprites = BattleSprites.load()
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        // A fixed world height keeps the fighters the same size on every device; the width follows
        // the aspect ratio, so a tall phone gets a taller arena rather than a stretched one.
        val worldWidth = WORLD_HEIGHT * (width.toFloat() / height.toFloat())
        camera.setToOrtho(false, worldWidth, WORLD_HEIGHT)
        camera.update()
        font.data.setScale(WORLD_HEIGHT / height.toFloat() * FONT_SCALE)
    }

    override fun render() {
        val delta = min(Gdx.graphics.deltaTime, MAX_DELTA)
        val state = bridge.state()
        val now = android.os.SystemClock.elapsedRealtime()

        advance(delta, state)
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        applyShake()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        val worldWidth = camera.viewportWidth
        val ground = WORLD_HEIGHT * GROUND_Y
        // A knight has his own idle animation; only a monster needs the bob to look alive.
        val bob = if (state.rightIsHero) 0f else MathUtils.sin(clock * IDLE_SPEED) * IDLE_BOB

        val right = rightSize(state)
        val leftA = leftArt(state)
        val rightA = rightArt(state)
        val left = heroSize(leftA)
        // A monster always closes in; only a player's school decides otherwise.
        val rightCloses = !state.rightIsHero || !rightA.isRanged
        val rightHomeX = worldWidth * RIGHT_X
        val leftHomeX = worldWidth * LEFT_X

        // Stop short of the target rather than on top of it: two sprites overlapping reads as a
        // drawing glitch, not a strike.
        var leftX = leftHomeX
        var leftY = ground
        leftLunge?.let {
            if (!leftA.isRanged) {
                val reach = lungeReach(it.elapsed)
                val touch = rightHomeX - right.halfWidth - left.halfWidth - LUNGE_GAP
                leftX = MathUtils.lerp(leftHomeX, touch, reach)
                leftY = ground + MathUtils.sin(reach * MathUtils.PI) * LUNGE_ARC
            }
        }
        if (leftLunge == null && leftStagger > 0f) {
            leftX -= leftStagger / STAGGER_SECONDS * STAGGER_SHIFT
        }

        var rightX = rightHomeX
        var rightY = ground + bob
        rightLunge?.let {
            if (rightCloses) {
                val reach = lungeReach(it.elapsed)
                val touch = leftHomeX + left.halfWidth + right.halfWidth + LUNGE_GAP
                rightX = MathUtils.lerp(rightHomeX, touch, reach)
                rightY = ground + bob + MathUtils.sin(reach * MathUtils.PI) * LUNGE_ARC
            }
        }
        if (rightLunge == null && rightStagger > 0f) {
            rightX += rightStagger / STAGGER_SECONDS * STAGGER_SHIFT
        }

        drawBackdrop(worldWidth)

        blend()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawGround(worldWidth, ground)
        drawShadow(rightX, ground, right.halfWidth)
        drawShadow(leftX, ground, left.halfWidth)
        shapes.end()

        batch.begin()
        if (state.rightIsHero) {
            drawKnight(
                rightX, rightY, mirrored = true, clip = rightClip, elapsed = rightElapsed,
                art = rightArt(state),
            )
        } else {
            drawMonster(state, right, rightX, rightY)
        }
        drawKnight(
            leftX, leftY, mirrored = false, clip = leftClip, elapsed = leftElapsed,
            art = leftA,
        )

        val leftShot = shotFor(
            leftLunge, leftA, leftX + left.halfWidth, rightX - right.halfWidth,
            ground + HERO_BODY_WORLD * SHOT_HEIGHT, toRight = true,
        )
        val rightShot = if (state.rightIsHero) {
            shotFor(
                rightLunge, rightA, rightX - right.halfWidth, leftX + left.halfWidth,
                ground + HERO_BODY_WORLD * SHOT_HEIGHT, toRight = false,
            )
        } else {
            null
        }
        drawArrow(leftShot)
        drawArrow(rightShot)
        batch.end()

        blend()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawCastBar(state, now, rightX, rightY, right)
        drawComboGlow(state.leftCombo, leftX, ground)
        drawComboGlow(state.rightCombo, rightX, ground)
        drawSpell(leftShot)
        drawSpell(rightShot)
        shapes.end()

        batch.begin()
        drawFloaters()
        batch.end()
    }

    // --- simulation ---------------------------------------------------------

    /** Age the transient effects, then take in anything that happened since the last frame. */
    private fun advance(delta: Float, state: ArenaState) {
        clock += delta
        leftElapsed += delta
        rightElapsed += delta
        rightFlinch = max(0f, rightFlinch - delta)
        leftFlinch = max(0f, leftFlinch - delta)
        shake = max(0f, shake - delta)
        leftStagger = max(0f, leftStagger - delta)
        rightStagger = max(0f, rightStagger - delta)

        // Clip timings belong to the pack, so a clip that has run out is asked of that fighter's
        // own artwork rather than of a table shared by all three schools.
        val leftPlaying = leftArt(state).clip(leftClip)
        val rightPlaying = rightArt(state).clip(rightClip)
        if (!leftPlaying.looping && leftElapsed >= leftPlaying.seconds) playLeft(HeroClip.IDLE)
        if (!rightPlaying.looping && rightElapsed >= rightPlaying.seconds) playRight(HeroClip.IDLE)

        val iterator = floaters.iterator()
        while (iterator.hasNext()) {
            val floater = iterator.next()
            floater.age += delta
            if (floater.age >= FLOATER_LIFE) iterator.remove()
        }

        val worldWidth = if (camera.viewportWidth > 0f) camera.viewportWidth else WORLD_HEIGHT
        for (event in bridge.drainEvents()) {
            when (event) {
                is ArenaEvent.LeftHit ->
                    enqueue(pendingLeft, Lunge(event.damage, critical = event.critical))

                is ArenaEvent.RightHit ->
                    enqueue(pendingRight, Lunge(event.damage, heavy = event.heavy))

                is ArenaEvent.SkillCast -> {
                    if (event.onLeft) playLeft(HeroClip.CAST) else playRight(HeroClip.CAST)
                    val caster = if (event.onLeft) leftArt(state) else rightArt(state)
                    addFloater(
                        text = event.effect,
                        x = worldWidth * (if (event.onLeft) LEFT_X else RIGHT_X),
                        y = WORLD_HEIGHT * GROUND_Y + heroSize(caster).height,
                        color = COLOR_SKILL,
                        scale = SKILL_TEXT_SCALE,
                    )
                }

                ArenaEvent.Down -> {
                    rightFlinch = FLINCH_SECONDS * 3f
                    shake = SHAKE_SECONDS * 2f
                    leftLunge = null
                    rightLunge = null
                    pendingLeft.clear()
                    pendingRight.clear()
                }

                is ArenaEvent.Miss ->
                    if (event.onLeft) leftStagger = STAGGER_SECONDS
                    else rightStagger = STAGGER_SECONDS
            }
        }

        // The swing starts when the blow leaves, not when it lands: the clip has to be most of the
        // way through by the time the sword reaches the other fighter.
        if (leftLunge == null && pendingLeft.isNotEmpty()) playLeft(HeroClip.ATTACK)
        if (state.rightIsHero && rightLunge == null && pendingRight.isNotEmpty()) {
            playRight(HeroClip.ATTACK)
        }

        // Everything that used to fire the instant an event arrived (flinch, floater, shake) now
        // fires when the lunge it belongs to actually reaches the touch mark.
        val rightLabelY = WORLD_HEIGHT * GROUND_Y +
            if (state.rightIsHero) heroSize(rightArt(state)).height else MONSTER_LABEL_LIFT
        leftLunge = stepLunge(leftLunge, pendingLeft, delta) { lunge ->
            rightFlinch = FLINCH_SECONDS
            if (state.rightIsHero) playRight(HeroClip.HURT)
            addFloater(
                text = "-${lunge.damage}",
                x = worldWidth * RIGHT_X,
                y = rightLabelY,
                color = if (lunge.critical) COLOR_CRIT else COLOR_HIT,
                scale = if (lunge.critical) CRIT_TEXT_SCALE else 1f,
            )
        }
        rightLunge = stepLunge(rightLunge, pendingRight, delta) { lunge ->
            leftFlinch = FLINCH_SECONDS
            playLeft(HeroClip.HURT)
            shake = if (lunge.heavy) SHAKE_SECONDS * 2f else SHAKE_SECONDS
            addFloater(
                text = "-${lunge.damage}",
                x = worldWidth * LEFT_X,
                y = WORLD_HEIGHT * GROUND_Y + heroSize(leftArt(state)).height,
                color = COLOR_TAKEN,
                scale = 1f,
            )
        }

        if (state.finished) {
            floaters.clear()
            leftLunge = null
            rightLunge = null
            pendingLeft.clear()
            pendingRight.clear()
        }
    }

    private fun playLeft(clip: HeroClip) {
        leftClip = clip
        leftElapsed = 0f
    }

    private fun playRight(clip: HeroClip) {
        rightClip = clip
        rightElapsed = 0f
    }

    private fun enqueue(queue: ArrayDeque<Lunge>, lunge: Lunge) {
        // Bounded the same way ArenaBridge.emit is: a paused renderer must not build an unbounded
        // backlog of blows to play catch-up on when it resumes.
        while (queue.size >= MAX_PENDING_LUNGES) queue.removeFirst()
        queue.addLast(lunge)
    }

    /** Steps one lunge forward a frame and fires [onImpact] the instant it crosses the touch mark. */
    private fun stepLunge(
        current: Lunge?,
        queue: ArrayDeque<Lunge>,
        delta: Float,
        onImpact: (Lunge) -> Unit,
    ): Lunge? {
        val lunge = current ?: queue.removeFirstOrNull() ?: return null
        lunge.elapsed += delta
        if (!lunge.impacted && lunge.elapsed >= LUNGE_ADVANCE) {
            lunge.impacted = true
            onImpact(lunge)
        }
        return if (lunge.elapsed >= LUNGE_TOTAL) null else lunge
    }

    /**
     * How far a lunge has travelled, 0..1, across its three legs.
     *
     * The advance is quick and decisive (ease-out); the return is unhurried (ease-in-out) -- a
     * strike has to leave faster than it comes back, or it reads as walking both ways.
     */
    private fun lungeReach(elapsed: Float): Float = when {
        elapsed < LUNGE_ADVANCE -> {
            val p = elapsed / LUNGE_ADVANCE
            1f - (1f - p) * (1f - p)
        }
        elapsed < LUNGE_ADVANCE + LUNGE_HOLD -> 1f
        else -> {
            val p = ((elapsed - LUNGE_ADVANCE - LUNGE_HOLD) / LUNGE_RETURN).coerceIn(0f, 1f)
            1f - MathUtils.sin(p * MathUtils.PI / 2f)
        }
    }

    private fun addFloater(text: String, x: Float, y: Float, color: Color, scale: Float) {
        // Spread side by side rather than stacking, so two numbers in the same instant stay
        // readable instead of drawing on top of one another.
        val offset = (floaters.size % FLOATER_SPREAD) * FLOATER_SPACING
        floaters.add(Floater(text, x + offset - FLOATER_SPACING, y, color, scale))
    }

    /**
     * Nudges the camera for a beat after a blow.
     *
     * Applied to the camera rather than to each shape, so nothing can drift out of alignment with
     * anything else, and one line puts it back.
     */
    private fun applyShake() {
        val worldWidth = camera.viewportWidth
        camera.position.set(worldWidth / 2f, WORLD_HEIGHT / 2f, 0f)
        if (shake > 0f) {
            val strength = shake / SHAKE_SECONDS * SHAKE_PIXELS
            camera.position.add(
                MathUtils.random(-strength, strength),
                MathUtils.random(-strength, strength),
                0f,
            )
        }
        camera.update()
    }

    // --- drawing ------------------------------------------------------------

    /**
     * SpriteBatch turns blending off when it ends, so every shape pass has to turn it back on --
     * and the function has to be set at least once, because the GL default multiplies alpha away.
     */
    private fun blend() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** The artwork each side fights in. A monster on the right leaves [rightArt] unread. */
    private fun leftArt(state: ArenaState): HeroArt =
        BattleArt.heroFor(state.leftClassCode, state.leftSkinCode)

    private fun rightArt(state: ArenaState): HeroArt =
        BattleArt.heroFor(state.rightClassCode, state.rightSkinCode)

    /**
     * How much room a fighter takes. The frame is far wider than they are; [halfWidth] is the
     * person.
     *
     * Scaled off the body rather than the frame, so three packs drawn at three resolutions put
     * three fighters of the same height on the stage. Scaling off the frame would make whoever
     * came from the pack with the most empty space around them the smallest fighter.
     */
    private fun heroSize(art: HeroArt): FighterSize {
        val height = HERO_BODY_WORLD * art.frameHeight / art.bodyHeight
        val width = height * art.frameWidth / art.frameHeight
        return FighterSize(width, height, width * art.bodyHalfWidth)
    }

    private fun rightSize(state: ArenaState): FighterSize =
        if (state.rightIsHero) heroSize(rightArt(state)) else monsterSize(state)

    /** How wide and tall this fight's monster stands, sprite or silhouette. */
    private fun monsterSize(state: ArenaState): FighterSize {
        val scale = MONSTER_SCALE * if (state.isBoss) BOSS_SCALE else 1f
        val art = BattleArt.monster(state.artCode)
        val height = (art?.worldHeight ?: FALLBACK_HEIGHT) * scale
        val width = if (art == null) height else height * art.width / art.height
        return FighterSize(width, height, width / 2f)
    }

    /**
     * The stage, covering the viewport the way a wallpaper covers a wall: scaled to whichever edge
     * needs it more and centred, so a wide phone crops the hills instead of stretching them.
     */
    private fun drawBackdrop(worldWidth: Float) {
        val backdrop = sprites?.backdrop ?: return
        val scale = max(
            worldWidth / backdrop.regionWidth,
            WORLD_HEIGHT / backdrop.regionHeight,
        )
        val width = backdrop.regionWidth * scale
        val height = backdrop.regionHeight * scale
        batch.begin()
        batch.setColor(Color.WHITE)
        batch.draw(backdrop, (worldWidth - width) / 2f, (WORLD_HEIGHT - height) / 2f, width, height)
        batch.end()
    }

    private fun drawGround(worldWidth: Float, ground: Float) {
        // Only what the backdrop cannot do: a soft darkening under the fighters that ties them to
        // the floor the painting already drew.
        shapes.rect(
            0f,
            0f,
            worldWidth,
            ground,
            COLOR_GROUND_LOW,
            COLOR_GROUND_LOW,
            COLOR_GROUND_HIGH,
            COLOR_GROUND_HIGH,
        )
    }

    private fun drawShadow(x: Float, ground: Float, halfWidth: Float) {
        shapes.color = COLOR_SHADOW
        shapes.ellipse(
            x - halfWidth,
            ground - halfWidth * SHADOW_FLATNESS / 2f,
            halfWidth * 2f,
            halfWidth * SHADOW_FLATNESS,
        )
    }

    private fun drawMonster(state: ArenaState, size: FighterSize, x: Float, y: Float) {
        val hurt = rightFlinch / FLINCH_SECONDS
        // Squash on the horizontal and stretch on the vertical by the same amount, so a hit reads
        // as impact rather than as the monster changing size.
        val squash = 1f + hurt * SQUASH
        val width = size.width * squash
        val height = size.height / squash

        val region = sprites?.monster(state.artCode)
        if (region == null) {
            batch.end()
            blend()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawSilhouette(state.artCode, x, y, width, height, hurt)
            shapes.end()
            batch.begin()
            return
        }

        // A hit whitens the sprite rather than recolouring it: the tile already carries the
        // creature's colour, and tinting it towards yellow would turn a wolf into a different wolf.
        tint.set(Color.WHITE).lerp(COLOR_HIT, hurt * HIT_TINT)
        batch.color = tint
        batch.draw(region, x - width / 2f, y, width, height)
        batch.setColor(Color.WHITE)
    }

    /** What a monster looks like when its `art_code` arrived after the sprite page was packed. */
    private fun drawSilhouette(
        artCode: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        hurt: Float,
    ) {
        val body = fallbackPalette(artCode)
        shapes.color = if (hurt > 0f) body.cpy().lerp(COLOR_HIT, hurt) else body
        shapes.ellipse(x - width / 2f, y, width, height)
        shapes.color = COLOR_EYE
        val eyeOffset = width * EYE_OFFSET
        val eyeHeight = y + height * EYE_HEIGHT
        shapes.circle(x - eyeOffset, eyeHeight, width * EYE_RADIUS)
        shapes.circle(x + eyeOffset, eyeHeight, width * EYE_RADIUS)
    }

    /**
     * One shot in flight, or null when the attacker is melee, idle, or the shot already landed.
     *
     * Crosses in [LUNGE_ADVANCE] -- the same window a lunge takes to reach the touch mark -- and
     * disappears the instant it arrives, which is the instant `stepLunge` fires the impact. So the
     * arrow visibly hits on the frame the damage number appears, without either one knowing about
     * the other.
     */
    private fun shotFor(
        lunge: Lunge?,
        art: HeroArt,
        fromX: Float,
        toX: Float,
        y: Float,
        toRight: Boolean,
    ): Shot? {
        if (!art.isRanged) return null
        val flying = lunge ?: return null
        if (flying.elapsed >= LUNGE_ADVANCE) return null
        val progress = (flying.elapsed / LUNGE_ADVANCE).coerceIn(0f, 1f)
        return Shot(
            x = MathUtils.lerp(fromX, toX, progress),
            y = y,
            toRight = toRight,
            style = art.attack,
            progress = progress,
        )
    }

    /** The archer's arrow. Falls back to a dart of colour when the sprite is missing. */
    private fun drawArrow(shot: Shot?) {
        if (shot == null || shot.style != AttackStyle.ARROW) return
        // Flipped rather than rotated: the sprite is drawn flat and the two fighters only ever
        // shoot along the ground line at each other.
        val sprite = sprites?.arrow(mirrored = !shot.toRight) ?: return
        val width = ARROW_WIDTH
        val height = width * sprite.regionHeight / sprite.regionWidth
        val left = if (shot.toRight) shot.x else shot.x - width
        batch.draw(sprite, left, shot.y - height / 2f, width, height)
    }

    /**
     * The mage's bolt, drawn rather than cut from a sheet.
     *
     * The wizard pack animates its spells inside the attack frames and ships no projectile of its
     * own, and a circle of light is a thing this renderer can already draw honestly. It grows as
     * it travels, so a bolt reads as gathering rather than sliding.
     */
    private fun drawSpell(shot: Shot?) {
        if (shot == null || shot.style != AttackStyle.SPELL) return
        val radius = SPELL_RADIUS * (0.6f + 0.4f * shot.progress)
        shapes.color = COLOR_SPELL_HALO
        shapes.circle(shot.x, shot.y, radius * 1.7f)
        shapes.color = COLOR_SPELL
        shapes.circle(shot.x, shot.y, radius)
    }

    /**
     * One knight, facing either way.
     *
     * The mirrored one is tinted rather than recoloured: the same armour in a different light
     * still reads as a second person, where two identical knights read as a drawing bug.
     */
    private fun drawKnight(
        x: Float,
        y: Float,
        mirrored: Boolean,
        clip: HeroClip,
        elapsed: Float,
        art: HeroArt,
    ) {
        val size = heroSize(art)
        // The frame's own feet, in case a re-pack ever leaves headroom under them.
        val sink = size.height * (1f - art.feet.toFloat() / art.frameHeight)
        val centre = if (mirrored) art.bodyCentreMirrored else art.bodyCentre
        val left = x - size.width * centre

        val frame = sprites?.hero(art, clip, elapsed, mirrored)
        if (frame == null) {
            batch.end()
            blend()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.color = if (mirrored) COLOR_RIVAL else COLOR_PLAYER
            shapes.rect(
                x - FALLBACK_PLAYER_WIDTH / 2f,
                y,
                FALLBACK_PLAYER_WIDTH,
                size.height * 0.7f,
            )
            shapes.end()
            batch.begin()
            return
        }

        // The knight has his own hurt frames, so the tint only has to carry the wrong answer -- a
        // beat of grey, which the clip has nothing to say about -- and, for the rival, the wash
        // that tells the two of them apart.
        val miss = (if (mirrored) rightStagger else leftStagger) / STAGGER_SECONDS
        tint.set(if (mirrored) COLOR_RIVAL_WASH else Color.WHITE).lerp(COLOR_STAGGER, miss * STAGGER_TINT)
        batch.color = tint
        batch.draw(frame, left, y - sink, size.width, size.height)
        batch.setColor(Color.WHITE)
    }

    /**
     * The wind-up, as a bar arcing over the monster.
     *
     * This is the one thing on screen that must be smooth, and the reason the arena is GL at all:
     * it is interpolated from a deadline on every frame, so it climbs continuously even though the
     * server only speaks ten times a second. A duo match has no wind-up -- neither fighter attacks
     * on a clock -- and leaves `castIntervalMs` at zero, which draws nothing.
     */
    private fun drawCastBar(
        state: ArenaState,
        nowMs: Long,
        x: Float,
        y: Float,
        size: FighterSize,
    ) {
        if (state.finished || state.castIntervalMs <= 0) return
        val fraction = state.castFraction(nowMs)
        val width = size.width * CAST_BAR_WIDTH
        val height = CAST_BAR_HEIGHT
        val top = y + size.height + CAST_BAR_LIFT

        shapes.color = COLOR_CAST_FRAME
        shapes.rect(
            x - width / 2f - CAST_BAR_EDGE,
            top - CAST_BAR_EDGE,
            width + CAST_BAR_EDGE * 2f,
            height + CAST_BAR_EDGE * 2f,
        )
        shapes.color = COLOR_CAST_TRACK
        shapes.rect(x - width / 2f, top, width, height)
        shapes.color = if (state.enragedNext) COLOR_CAST_ENRAGED else COLOR_CAST
        shapes.rect(x - width / 2f, top, width * fraction, height)
    }

    /** A combo is worth seeing on the fighter who earned it. */
    private fun drawComboGlow(combo: Int, x: Float, ground: Float) {
        if (combo < COMBO_GLOW_FROM) return
        val pulse = 1f + MathUtils.sin(clock * COMBO_PULSE_SPEED) * COMBO_PULSE
        shapes.color = COLOR_COMBO
        shapes.ellipse(
            x - COMBO_GLOW * pulse,
            ground - COMBO_GLOW * SHADOW_FLATNESS / 2f,
            COMBO_GLOW * 2f * pulse,
            COMBO_GLOW * SHADOW_FLATNESS,
        )
    }

    private fun drawFloaters() {
        for (floater in floaters) {
            val progress = floater.age / FLOATER_LIFE
            font.data.setScale(font.data.scaleX * floater.scale)
            font.color = floater.color.cpy().also { it.a = 1f - progress }
            font.draw(batch, floater.text, floater.x, floater.y + progress * FLOATER_RISE)
            font.data.setScale(font.data.scaleX / floater.scale)
        }
        font.color = Color.WHITE
    }

    override fun dispose() {
        if (this::shapes.isInitialized) shapes.dispose()
        if (this::batch.isInitialized) batch.dispose()
        if (this::font.isInitialized) font.dispose()
        sprites?.dispose()
        sprites = null
    }

    private class Floater(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Color,
        val scale: Float,
        var age: Float = 0f,
    )

    /** How much room one fighter takes, in world units. [halfWidth] is what a blow stops short of. */
    private class FighterSize(val width: Float, val height: Float, val halfWidth: Float)

    /**
     * A blow in flight: lunge in, touch, lunge back.
     *
     * The damage is already settled server-side; this only holds enough to know what number to
     * draw and how hard to shake at the touch. [impacted] is a one-way latch, so a stretched frame
     * can never fire the touch twice.
     */
    /** A projectile mid-flight: where it is, which way it points and how far along it is. */
    private class Shot(
        val x: Float,
        val y: Float,
        val toRight: Boolean,
        val style: AttackStyle,
        val progress: Float,
    )

    private class Lunge(
        val damage: Int,
        val critical: Boolean = false,
        val heavy: Boolean = false,
        var elapsed: Float = 0f,
        var impacted: Boolean = false,
    )

    private companion object {
        /** World units. Everything else is expressed against this, so the arena scales as one. */
        const val WORLD_HEIGHT = 270f
        const val MAX_DELTA = 0.05f

        /** The line both fighters stand on, as a fraction of the world height. */
        const val GROUND_Y = 0.13f
        const val LEFT_X = 0.26f
        const val RIGHT_X = 0.72f

        /**
         * How tall a fighter stands, in world units -- the person, not the frame around them.
         *
         * Was a frame height (165) back when one pack served everyone; a frame is now a different
         * shape per school, so the constant that has to stay fixed is the body. 132 is what the
         * knight measured at the old number, so the stage did not change size when this did.
         */
        const val HERO_BODY_WORLD = 132f
        /**
         * Every creature, scaled together.
         *
         * The per-monster heights in [BattleArt] set them against each other -- a dragon against a
         * slime -- and this sets all of them against the band they stand in. Tuned so the tallest
         * boss plus its wind-up bar still clears the top of the stage.
         */
        const val MONSTER_SCALE = 1.25f
        const val FALLBACK_HEIGHT = 92f
        const val FALLBACK_PLAYER_WIDTH = 36f
        const val BOSS_SCALE = 1.28f
        const val SQUASH = 0.18f
        const val HIT_TINT = 0.75f
        const val STAGGER_TINT = 0.6f
        const val EYE_OFFSET = 0.17f
        const val EYE_HEIGHT = 0.62f
        const val EYE_RADIUS = 0.055f

        const val SHADOW_FLATNESS = 0.34f
        const val COMBO_GLOW_FROM = 3
        const val COMBO_GLOW = 26f
        const val COMBO_PULSE = 0.12f
        const val COMBO_PULSE_SPEED = 5f

        const val CAST_BAR_WIDTH = 1.15f
        const val CAST_BAR_HEIGHT = 7f
        const val CAST_BAR_LIFT = 10f
        const val CAST_BAR_EDGE = 1.6f

        const val FLINCH_SECONDS = 0.28f
        const val SHAKE_SECONDS = 0.18f
        const val SHAKE_PIXELS = 6f
        const val IDLE_SPEED = 1.8f
        const val IDLE_BOB = 3f
        const val MONSTER_LABEL_LIFT = 120f

        /**
         * A blow flying in: advance, hold at the touch, return. Comfortably under the server's
         * 600ms post-answer lockout, so two lunges never overlap.
         */
        const val LUNGE_ADVANCE = 0.18f
        const val LUNGE_HOLD = 0.10f
        const val LUNGE_RETURN = 0.24f
        const val LUNGE_TOTAL = LUNGE_ADVANCE + LUNGE_HOLD + LUNGE_RETURN
        /**
         * Where a shot leaves and arrives, as a fraction of the fighter's body -- chest height.
         *
         * Measured against [HERO_BODY_WORLD] rather than the frame, because a frame is a different
         * shape per school: the mage's box is a third taller than the archer's for the same size
         * fighter, and taking the fraction of *that* would send his bolt past his own head.
         */
        const val SHOT_HEIGHT = 0.55f
        const val ARROW_WIDTH = 34f
        const val SPELL_RADIUS = 10f

        const val LUNGE_ARC = 14f
        const val LUNGE_GAP = 6f
        const val MAX_PENDING_LUNGES = 3
        const val STAGGER_SECONDS = 0.2f
        const val STAGGER_SHIFT = 5f

        const val FLOATER_LIFE = 1.1f
        const val FLOATER_RISE = 42f
        const val FLOATER_SPREAD = 3
        const val FLOATER_SPACING = 22f
        const val FONT_SCALE = 2.6f
        const val CRIT_TEXT_SCALE = 1.5f
        const val SKILL_TEXT_SCALE = 0.75f

        val COLOR_GROUND_LOW: Color = Color(0.04f, 0.03f, 0.06f, 0.55f)
        val COLOR_GROUND_HIGH: Color = Color(0.04f, 0.03f, 0.06f, 0f)
        val COLOR_SHADOW: Color = Color(0f, 0f, 0f, 0.34f)
        val COLOR_EYE: Color = Color(0.97f, 0.97f, 0.94f, 1f)
        val COLOR_PLAYER: Color = Color(0.35f, 0.62f, 0.9f, 1f)
        /** The duo opponent, when there is no sprite page to draw him from. */
        val COLOR_RIVAL: Color = Color(0.86f, 0.42f, 0.38f, 1f)
        /** And when there is: the same knight under a warmer light, never a different knight. */
        val COLOR_RIVAL_WASH: Color = Color(1f, 0.72f, 0.66f, 1f)
        val COLOR_COMBO: Color = Color(1f, 0.78f, 0.28f, 0.3f)
        val COLOR_HIT: Color = Color(1f, 0.95f, 0.75f, 1f)
        val COLOR_CRIT: Color = Color(1f, 0.5f, 0.2f, 1f)
        val COLOR_TAKEN: Color = Color(0.93f, 0.32f, 0.29f, 1f)
        val COLOR_SKILL: Color = Color(0.55f, 0.85f, 0.98f, 1f)

        /** The mage's bolt: a bright core inside a softer halo, drawn rather than cut. */
        val COLOR_SPELL: Color = Color(0.85f, 0.80f, 1f, 1f)
        val COLOR_SPELL_HALO: Color = Color(0.55f, 0.35f, 0.95f, 0.45f)
        val COLOR_CAST_FRAME: Color = Color(0.05f, 0.04f, 0.07f, 0.85f)
        val COLOR_CAST_TRACK: Color = Color(1f, 1f, 1f, 0.18f)
        val COLOR_CAST: Color = Color(0.95f, 0.62f, 0.25f, 0.95f)
        val COLOR_CAST_ENRAGED: Color = Color(0.95f, 0.25f, 0.25f, 0.95f)
        val COLOR_STAGGER: Color = Color(0.55f, 0.55f, 0.58f, 1f)

        /** A code the sprite page has never heard of still draws something, rather than nothing. */
        fun fallbackPalette(artCode: String): Color = when {
            artCode.contains("SLIME") -> Color(0.42f, 0.82f, 0.45f, 1f)
            artCode.contains("GOBLIN") -> Color(0.55f, 0.7f, 0.3f, 1f)
            artCode.contains("WOLF") -> Color(0.55f, 0.57f, 0.62f, 1f)
            artCode.contains("GOLEM") || artCode.contains("STONE") -> Color(0.55f, 0.48f, 0.4f, 1f)
            artCode.contains("WRAITH") || artCode.contains("LICH") -> Color(0.6f, 0.55f, 0.85f, 1f)
            artCode.contains("DRAKE") || artCode.contains("DRAGON") -> Color(0.85f, 0.45f, 0.3f, 1f)
            else -> Color(0.6f, 0.6f, 0.65f, 1f)
        }
    }
}
