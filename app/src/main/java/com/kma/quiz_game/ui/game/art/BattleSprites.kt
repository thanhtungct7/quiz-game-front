package com.kma.quiz_game.ui.game.art

import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

/**
 * The arena's artwork, loaded once and held for the life of the fight.
 *
 * A handful of pages, no atlas file. A [com.badlogic.gdx.graphics.g2d.TextureAtlas] would buy
 * nothing here -- there are at most four textures in a fight, each bound once per frame, and the
 * region table is already a compile-time constant in [BattleArt] -- while costing a generated
 * manifest that can drift out of step with the pages it describes.
 *
 * [load] answers null rather than throwing. A missing or corrupt page is a reason to draw the old
 * silhouettes, not a reason to take down a fight the player is in the middle of.
 */
class BattleSprites private constructor(
    private val monsterPage: Texture,
    private val backdropPage: Texture,
) : Disposable {

    val backdrop: TextureRegion = TextureRegion(backdropPage)

    /**
     * Hero pages, loaded the first time a fighter needs one and kept for the rest of the fight.
     *
     * Lazily rather than up front because a fight uses at most two of them -- one school per side
     * -- and which two is not known when the surface is created. A page that fails to load is
     * remembered as a failure so a broken file is not re-decoded on every single frame.
     *
     * Only ever touched from the GL thread, inside `render`, which is also the only thread allowed
     * to create a [Texture].
     */
    private val heroPages = HashMap<String, HeroPage?>()

    private class HeroPage(val texture: Texture, val art: HeroArt) {
        val frames: List<TextureRegion> = buildList {
            val cells = (texture.width / art.frameWidth) * (texture.height / art.frameHeight)
            repeat(cells) { index ->
                add(
                    TextureRegion(
                        texture,
                        (index % art.columns) * art.frameWidth,
                        (index / art.columns) * art.frameHeight,
                        art.frameWidth,
                        art.frameHeight,
                    )
                )
            }
        }

        /**
         * Frames flipped to face the other way.
         *
         * Built once rather than flipped on the fly: a [TextureRegion] flip mutates the region's
         * UVs, and doing that to a shared frame mid-draw would leave whichever fighter is drawn
         * next facing backwards.
         */
        val mirrored: List<TextureRegion> =
            frames.map { frame -> TextureRegion(frame).apply { flip(true, false) } }
    }

    private val monsterRegions: Map<String, TextureRegion> =
        BattleArt.MONSTERS.mapValues { (_, art) ->
            TextureRegion(monsterPage, art.x, art.y, art.width, art.height)
        }

    /**
     * One frame of one fighter. [mirrored] turns them around, for the duo opponent.
     *
     * Null when that school's page could not be loaded -- the caller draws a silhouette, exactly
     * as it did when the whole artwork was missing.
     */
    fun hero(
        art: HeroArt,
        clip: HeroClip,
        elapsed: Float,
        mirrored: Boolean = false,
    ): TextureRegion? {
        val page = heroPages.getOrPut(art.page) { loadHeroPage(art) } ?: return null
        val frames = if (mirrored) page.mirrored else page.frames
        return frames.getOrNull(art.clip(clip).frameAt(elapsed))
    }

    private fun loadHeroPage(art: HeroArt): HeroPage? = try {
        val texture = Texture(Gdx.files.internal(art.page)).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }
        HeroPage(texture, art)
    } catch (error: Throwable) {
        Log.w(TAG, "Hero page missing: ${art.page}", error)
        null
    }

    /** Null for an `art_code` this page was packed without -- the caller draws a silhouette. */
    fun monster(artCode: String): TextureRegion? = monsterRegions[artCode]

    /**
     * The archer's arrow, loaded on first use like a hero page.
     *
     * Its own texture rather than a corner of one of the pages: it belongs to the huntress pack,
     * is 24x5, and packing a sliver that thin into a grid of 46x45 frames would waste more space
     * than the extra bind costs. Null means the file is missing, and the caller falls back to
     * drawing a dart.
     */
    fun arrow(mirrored: Boolean): TextureRegion? {
        if (!arrowLoaded) {
            arrowLoaded = true
            runCatching {
                val texture = Texture(Gdx.files.internal(BattleArt.ARROW)).apply {
                    setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                }
                arrowTexture = texture
                arrowRegion = TextureRegion(texture)
                arrowMirrored = TextureRegion(texture).apply { flip(true, false) }
            }.onFailure { Log.w(TAG, "Arrow sprite missing", it) }
        }
        return if (mirrored) arrowMirrored else arrowRegion
    }

    private var arrowLoaded = false
    private var arrowTexture: Texture? = null
    private var arrowRegion: TextureRegion? = null
    private var arrowMirrored: TextureRegion? = null

    override fun dispose() {
        heroPages.values.forEach { it?.texture?.dispose() }
        arrowTexture?.dispose()
        monsterPage.dispose()
        backdropPage.dispose()
    }

    companion object {
        private const val TAG = "BattleSprites"

        fun load(): BattleSprites? = try {
            // Nearest on the sprites and linear on the backdrop, because they are different kinds
            // of picture: the sprites are pixel art drawn ten times their size and have to stay
            // crisp, the backdrop is a smooth painting and would band if it were not filtered.
            // Hero pages are not loaded here -- see [heroPages].
            val monsters = Texture(Gdx.files.internal(BattleArt.MONSTER_PAGE)).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            val backdrop = Texture(Gdx.files.internal(BattleArt.BACKDROP)).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
            BattleSprites(monsters, backdrop)
        } catch (error: Throwable) {
            Log.w(TAG, "Battle artwork missing; falling back to drawn shapes", error)
            null
        }
    }
}
