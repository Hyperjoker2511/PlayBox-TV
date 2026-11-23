package com.playbox.tv

import org.junit.Assert.assertEquals
import org.junit.Test

// ---------- Patch 1.3.3 (Nova) ----------
// The whole-app design switch and the fourth player look. NOVA is
// the default app design (the from-scratch design); QWEN is the kept
// option. The player style "nova" maps to the solid bottom bar; an
// unknown or missing player-ui name still falls back to LUMEN (the
// design-aware default — NOVA for the Nova design, LUMEN for Qwen —
// lives in playerUiStyle(context), which needs a Context and is
// therefore not a pure unit test).

class Patch10Test {

    @Test
    fun appDesign_qwenByName() {
        assertEquals(AppDesign.QWEN, appDesignFromName("qwen"))
    }

    @Test
    fun appDesign_novaIsTheDefault() {
        assertEquals(AppDesign.NOVA, appDesignFromName(null))
        assertEquals(AppDesign.NOVA, appDesignFromName(""))
        assertEquals(AppDesign.NOVA, appDesignFromName("nova"))
        assertEquals(AppDesign.NOVA, appDesignFromName("something-else"))
    }

    @Test
    fun playerUiStyle_novaByName() {
        assertEquals(PlayerUiStyle.NOVA, playerUiStyleFromName("nova"))
    }

    @Test
    fun playerUiStyle_otherNamesUnchanged() {
        assertEquals(PlayerUiStyle.LUMEN, playerUiStyleFromName("lumen"))
        assertEquals(PlayerUiStyle.LIQUID_GLASS, playerUiStyleFromName("liquid_glass"))
        assertEquals(PlayerUiStyle.PRISM, playerUiStyleFromName("prism"))
    }

    @Test
    fun playerUiStyle_unknownStillFallsBackToLumen() {
        assertEquals(PlayerUiStyle.LUMEN, playerUiStyleFromName(null))
        assertEquals(PlayerUiStyle.LUMEN, playerUiStyleFromName(""))
        assertEquals(PlayerUiStyle.LUMEN, playerUiStyleFromName("something-else"))
    }
}
