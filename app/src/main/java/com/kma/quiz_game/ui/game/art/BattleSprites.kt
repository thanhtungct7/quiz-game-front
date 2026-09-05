package com.kma.quiz_game.ui.game.art

import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

/**
 * The arena's artwork, loaded once and held for the life of the fight.
 *
 * Three pages, no atlas file. A [com.badlogic.gdx.graphics.g2d.TextureAtlas] would buy nothing
 * here -- there are exactly three textures, they are bound once each per frame, and the region
 * table is already a compile-time constant in [BattleArt] -- while costing a generated manifest
 * that can drift out of step with the pages it describes.
 *
 * [load] answers null rather than throwing. A missing or corrupt page is a reason to draw the old
 * silhouettes, not a reason to take down a fight the player is in the middle of.
 */
class BattleSprites private constructor(
    private val heroPage: Texture,
    private val monsterPage: Texture,
    private val backdropPage: Texture,
) : Disposable {

    val backdrop: TextureRegion = TextureRegion(backdropPage)

    /** Frame `n` of the hero page, in the order [BattleArt] numbers them. */
    private val heroFrames: List<TextureRegion> = buildList {
        val columns = BattleArt.HERO_COLUMNS
        val width = BattleArt.HERO_FRAME_WIDTH
        val height = BattleArt.HERO_FRAME_HEIGHT
        val cells = (heroPage.width / width) * (heroPage.height / height)
        repeat(cells) { index ->
            add(
                TextureRegion(
                    heroPage,
                    (index % columns) * width,
                    (index / columns) * height,
                    width,
                    height,
                )
            )
        }
    }

    private val monsterRegions: Map<String, TextureRegion> =
        BattleArt.MONSTERS.mapValues { (_, art) ->
            TextureRegion(monsterPage, art.x, art.y, art.width, art.height)
        }

    /**
     * Frames flipped to face the other way.
     *
     * Built once here rather than by flipping on the fly: a [TextureRegion] flip mutates the
     * region's UVs, and doing that to a shared frame mid-draw would leave whichever fighter is
     * drawn next facing backwards.
     */
    private val heroFramesMirrored: List<TextureRegion> =
        heroFrames.map { frame -> TextureRegion(frame).apply { flip(true, false) } }

    /** One frame of the knight. [mirrored] turns him around, for the duo opponent. */
    fun hero(clip: HeroClip, elapsed: Float, mirrored: Boolean = false): TextureRegion =
        (if (mirrored) heroFramesMirrored else heroFrames)[clip.frameAt(elapsed)]

    /** Null for an `art_code` this page was packed without -- the caller draws a silhouette. */
    fun monster(artCode: String): TextureRegion? = monsterRegions[artCode]

    override fun dispose() {
        heroPage.dispose()
        monsterPage.dispose()
        backdropPage.dispose()
    }

    companion object {
        private const val TAG = "BattleSprites"

        fun load(): BattleSprites? = try {
            // Nearest on the sprites and linear on the backdrop, because they are different kinds
            // of picture: the sprites are pixel art drawn ten times their size and have to stay
            // crisp, the backdrop is a smooth painting and would band if it were not filtered.
            val hero = Texture(Gdx.files.internal(BattleArt.HERO_PAGE)).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            val monsters = Texture(Gdx.files.internal(BattleArt.MONSTER_PAGE)).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            }
            val backdrop = Texture(Gdx.files.internal(BattleArt.BACKDROP)).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
            BattleSprites(hero, monsters, backdrop)
        } catch (error: Throwable) {
            Log.w(TAG, "Battle artwork missing; falling back to drawn shapes", error)
            null
        }
    }
}
