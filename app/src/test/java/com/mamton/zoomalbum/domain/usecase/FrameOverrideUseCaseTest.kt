package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameOverrideUseCaseTest {

    private val useCase = FrameOverrideUseCase()

    private fun frame(
        overrides: Map<String, FrameMembershipOverride> = emptyMap(),
    ) = CanvasNode.Frame(
        id = "frame",
        transform = Transform(),
        overrides = overrides,
    )

    @Test
    fun `applyOverride on empty overrides writes the new entry`() {
        val before = frame()
        val after = useCase.applyOverride(
            before, setOf("a"), MembershipState.Included, MembershipOrigin.User,
        )

        assertEquals(
            mapOf("a" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User)),
            after.overrides,
        )
    }

    @Test
    fun `applyOverride with Included replaces an existing Excluded entry`() {
        // Mutual exclusion: pinning a node that was previously detached clears the detach.
        val before = frame(
            overrides = mapOf(
                "a" to FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.User),
            ),
        )
        val after = useCase.applyOverride(
            before, setOf("a"), MembershipState.Included, MembershipOrigin.User,
        )

        assertEquals(MembershipState.Included, after.overrides["a"]?.state)
    }

    @Test
    fun `applyOverride is a no-op when the target entry already exists with the same state and origin`() {
        val existing = FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User)
        val before = frame(overrides = mapOf("a" to existing))
        val after = useCase.applyOverride(
            before, setOf("a"), MembershipState.Included, MembershipOrigin.User,
        )

        // Same map contents → use case returns the original frame instance.
        assertSame(before, after)
    }

    @Test
    fun `applyOverride with empty nodeIds returns the same frame instance`() {
        val before = frame()
        val after = useCase.applyOverride(
            before, emptySet(), MembershipState.Included, MembershipOrigin.User,
        )

        assertSame(before, after)
    }

    @Test
    fun `applyOverride writes entries for every node id in the set`() {
        val before = frame()
        val after = useCase.applyOverride(
            before,
            setOf("a", "b", "c"),
            MembershipState.Excluded,
            MembershipOrigin.User,
        )

        assertEquals(3, after.overrides.size)
        assertTrue(after.overrides.values.all { it.state == MembershipState.Excluded })
    }

    @Test
    fun `applyOverride preserves overrides for nodes outside the call`() {
        val before = frame(
            overrides = mapOf(
                "keep" to FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.User),
            ),
        )
        val after = useCase.applyOverride(
            before, setOf("new"), MembershipState.Included, MembershipOrigin.User,
        )

        assertEquals(MembershipState.Excluded, after.overrides["keep"]?.state)
        assertEquals(MembershipState.Included, after.overrides["new"]?.state)
    }

    @Test
    fun `clearOverrides drops entries for the requested ids`() {
        val before = frame(
            overrides = mapOf(
                "drop" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
                "keep" to FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.User),
            ),
        )
        val after = useCase.clearOverrides(before, setOf("drop"))

        assertEquals(setOf("keep"), after.overrides.keys)
    }

    @Test
    fun `clearOverrides on nonexistent ids returns the same frame instance`() {
        val before = frame(
            overrides = mapOf(
                "a" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
            ),
        )
        val after = useCase.clearOverrides(before, setOf("ghost"))

        assertSame(before, after)
    }
}
