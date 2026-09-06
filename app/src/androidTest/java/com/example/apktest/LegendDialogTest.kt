package com.example.apktest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.apktest.game.core.EliteNpcModifier
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.render.EliteNpcIcons
import com.example.apktest.game.render.NpcIcons
import com.example.apktest.ui.EliteNpcResources
import com.example.apktest.ui.LegendDialog
import com.example.apktest.ui.NpcIconView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegendDialogTest {
    @Test
    fun defaultClassicLegendHasNoEliteSection() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = LegendDialog.show(activity)
                try {
                    assertTrue(dialog.isShowing)
                    val texts = LegendDialog.textSnapshotForTesting(dialog)
                    assertTrue(texts.contains(activity.getString(R.string.legend_section_npcs)))
                    assertFalse(texts.contains(activity.getString(R.string.adventure_elite_summary_title)))
                    for (modifier in EliteNpcModifier.entries) {
                        val label = activity.getString(EliteNpcResources.nameFor(modifier))
                        assertFalse(texts.any { it.startsWith("$label\n") })
                    }
                } finally {
                    dialog.dismiss()
                }
            }
        }
    }

    @Test
    fun trackerLegendShowsOnlyRequestedEliteAndItsCounterplay() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val modifier = EliteNpcModifier.TRACKER
                val dialog = LegendDialog.show(activity, setOf(modifier))
                try {
                    assertTrue(dialog.isShowing)
                    val texts = LegendDialog.textSnapshotForTesting(dialog)
                    assertEquals(1, texts.count { it == activity.getString(R.string.adventure_elite_summary_title) })
                    val expected = activity.getString(EliteNpcResources.nameFor(modifier)) + "\n" +
                        activity.getString(EliteNpcResources.descriptionFor(modifier))
                    assertEquals(1, texts.count { it == expected })
                    for (policy in NpcPolicyType.entries) {
                        assertTrue(texts.contains(policy.label + "\n" + policy.description))
                    }
                    for (deferredName in intArrayOf(
                        R.string.adventure_elite_guardian_name,
                        R.string.adventure_elite_sprinter_name,
                        R.string.adventure_elite_jammer_name,
                        R.string.adventure_elite_sentinel_name
                    )) {
                        val label = activity.getString(deferredName)
                        assertFalse(texts.any { it.startsWith("$label\n") })
                    }
                } finally {
                    dialog.dismiss()
                }
                val classicDialog = LegendDialog.show(activity)
                try {
                    assertFalse(
                        LegendDialog.textSnapshotForTesting(classicDialog)
                            .contains(activity.getString(R.string.adventure_elite_summary_title))
                    )
                } finally {
                    classicDialog.dismiss()
                }
            }
        }
    }

    @Test
    fun iconUsesSharedBadgeAndLegacySetterClearsItWithoutChangingPolicyTint() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val policy = NpcPolicyType.PATROL_GUARD
                val modifier = EliteNpcModifier.TRACKER
                val icon = NpcIconView(activity)
                val size = 140
                icon.measure(
                    View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
                )
                icon.layout(0, 0, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    icon.setNpcPolicyType(policy, modifier)
                    icon.draw(canvas)
                    val badgeCenter = (size * 0.71f).toInt()
                    assertEquals(
                        EliteNpcIcons.androidColorsFor(modifier)[EliteNpcIcons.ACCENT_COLOR],
                        bitmap.getPixel(badgeCenter, badgeCenter)
                    )
                    val bodyColor = NpcIcons.androidColorsFor(policy).getValue('M')
                    assertEquals(bodyColor, bitmap.getPixel(size / 2, size / 2))
                    icon.setNpcPolicyType(policy)
                    icon.draw(canvas)
                    assertEquals(bodyColor, bitmap.getPixel(badgeCenter, badgeCenter))
                    assertEquals(bodyColor, bitmap.getPixel(size / 2, size / 2))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
