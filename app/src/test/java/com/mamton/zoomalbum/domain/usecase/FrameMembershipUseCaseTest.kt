package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameMembershipUseCaseTest {

    private val useCase = FrameMembershipUseCase()

    private fun frame(
        id: String = "frame",
        cx: Float = 0f,
        cy: Float = 0f,
        w: Float = 200f,
        h: Float = 200f,
        overrides: Map<String, FrameMembershipOverride> = emptyMap(),
    ) = CanvasNode.Frame(
        id = id,
        transform = Transform(cx = cx, cy = cy, w = w, h = h),
        overrides = overrides,
    )

    private fun media(
        id: String,
        cx: Float = 0f,
        cy: Float = 0f,
        w: Float = 50f,
        h: Float = 50f,
    ) = CanvasNode.Media(
        id = id,
        transform = Transform(cx = cx, cy = cy, w = w, h = h),
        mediaRefId = "ref_$id",
    )

    @Test
    fun `geometry alone - intersecting nodes are members, distant nodes are not`() {
        val f = frame()  // 200x200 centered at origin → [-100..100]
        val inside = media("a", cx = 0f, cy = 0f)
        val outside = media("b", cx = 500f, cy = 500f)

        val members = useCase.effectiveMembers(f, listOf(inside, outside))

        assertEquals(setOf("a"), members)
    }

    @Test
    fun `Excluded override removes a geometrically inside node`() {
        val f = frame(
            overrides = mapOf(
                "a" to FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.User),
            ),
        )
        val inside = media("a", cx = 0f, cy = 0f)
        val alsoInside = media("b", cx = 10f, cy = 10f)

        val members = useCase.effectiveMembers(f, listOf(inside, alsoInside))

        assertEquals(setOf("b"), members)
    }

    @Test
    fun `Included override adds a geometrically outside node`() {
        val f = frame(
            overrides = mapOf(
                "far" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
            ),
        )
        val far = media("far", cx = 1000f, cy = 1000f)

        val members = useCase.effectiveMembers(f, listOf(far))

        assertEquals(setOf("far"), members)
    }

    // isFrameBindable=false coverage waits on the first opt-out subclass (Widget /
    // Background). Subclasses of the sealed `CanvasNode` must live in the same package
    // and module — so a test-only stub can't be added here without leaking into the
    // sealed hierarchy.

    @Test
    fun `a frame is not a member of itself even when geometrically self-intersecting`() {
        val f = frame(id = "self")

        val members = useCase.effectiveMembers(f, listOf(f))

        assertEquals(emptySet<String>(), members)
    }

    @Test
    fun `Included override for a node not in the list is ignored - orphan refs tolerated`() {
        val f = frame(
            overrides = mapOf(
                "ghost" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
            ),
        )

        val members = useCase.effectiveMembers(f, nodes = emptyList())

        assertEquals(emptySet<String>(), members)
    }

}
