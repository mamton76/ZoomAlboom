package com.mamton.zoomalbum.domain.usecase

import com.mamton.zoomalbum.domain.model.CanvasNode
import com.mamton.zoomalbum.domain.model.FrameEditOptions
import com.mamton.zoomalbum.domain.model.FrameMembershipOverride
import com.mamton.zoomalbum.domain.model.MembershipOrigin
import com.mamton.zoomalbum.domain.model.MembershipState
import com.mamton.zoomalbum.domain.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApplyFrameEditUseCaseTest {

    private val useCase = ApplyFrameEditUseCase()

    private fun frame(
        cx: Float = 0f,
        cy: Float = 0f,
        w: Float = 200f,
        h: Float = 200f,
        overrides: Map<String, FrameMembershipOverride> = emptyMap(),
    ) = CanvasNode.Frame(
        id = "frame",
        transform = Transform(cx = cx, cy = cy, w = w, h = h),
        overrides = overrides,
    )

    private fun media(id: String, cx: Float, cy: Float) = CanvasNode.Media(
        id = id,
        transform = Transform(cx = cx, cy = cy, w = 50f, h = 50f),
        mediaRefId = "ref_$id",
    )

    @Test
    fun `rebindAfterEdit=true returns frameAfter unchanged`() {
        val before = frame(cx = 0f)
        val after = frame(cx = 500f)  // moved far away
        val all = listOf(before, media("a", 0f, 0f))
        val allAfter = listOf(after, media("a", 0f, 0f))

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = false, rebindAfterEdit = true),
        )

        assertSame(after, result)
    }

    @Test
    fun `rebindAfterEdit=false writes Included+RebindSuppressed for dropped members`() {
        // Before: 200x200 at origin contains node 'a' at (0,0). After: frame moves so 'a' falls out.
        val a = media("a", 0f, 0f)
        val before = frame(cx = 0f)
        val after = frame(cx = 500f)
        val all = listOf(before, a)
        val allAfter = listOf(after, a)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = false, rebindAfterEdit = false),
        )

        assertEquals(
            FrameMembershipOverride(MembershipState.Included, MembershipOrigin.RebindSuppressed),
            result.overrides["a"],
        )
    }

    @Test
    fun `rebindAfterEdit=false writes Excluded+RebindSuppressed for newly captured nodes`() {
        // Before: frame far from 'b'. After: frame moves to enclose 'b'.
        val b = media("b", 0f, 0f)
        val before = frame(cx = 500f)
        val after = frame(cx = 0f)
        val all = listOf(before, b)
        val allAfter = listOf(after, b)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = false, rebindAfterEdit = false),
        )

        assertEquals(
            FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.RebindSuppressed),
            result.overrides["b"],
        )
    }

    @Test
    fun `rebindAfterEdit=false with no membership change returns frameAfter unchanged`() {
        // Frame moves but the same node stays inside before and after.
        val a = media("a", 10f, 10f)
        val before = frame(cx = 0f)
        val after = frame(cx = 20f)  // small shift, 'a' still inside both
        val all = listOf(before, a)
        val allAfter = listOf(after, a)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = false, rebindAfterEdit = false),
        )

        assertSame(after, result)
    }

    @Test
    fun `transform-with-content move - members translate with frame, no suppression overrides written`() {
        // Move-with-content scenario: the frame shifts and member 'a' shifts by the same
        // delta. Geometry relationship unchanged → effectiveMembers identical → applyFrameEdit
        // returns frameAfter unchanged even with rebindAfterEdit=false.
        val before = frame(cx = 0f)
        val after = frame(cx = 500f)
        val memberBefore = media("a", 10f, 10f)            // inside `before` (frame at origin)
        val memberAfter = media("a", 510f, 10f)            // shifted to track the frame
        val all = listOf(before, memberBefore)
        val allAfter = listOf(after, memberAfter)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = true, rebindAfterEdit = false),
        )

        assertSame(after, result)
    }

    @Test
    fun `transform-with-content writes RebindSuppressed for stationary nodes newly enclosed`() {
        // Frame moves to cover a stationary node 'b' that wasn't a member before. Member
        // 'a' translates with the frame. rebindAfterEdit=false → 'b' must be Excluded
        // even though it's now geometrically inside; 'a' stays a member.
        val before = frame(cx = 0f)
        val after = frame(cx = 500f)
        val memberBefore = media("a", 10f, 10f)
        val memberAfter = media("a", 510f, 10f)
        val stationary = media("b", 510f, 10f)             // outside `before`, inside `after`
        val all = listOf(before, memberBefore, stationary)
        val allAfter = listOf(after, memberAfter, stationary)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = true, rebindAfterEdit = false),
        )

        assertEquals(
            FrameMembershipOverride(MembershipState.Excluded, MembershipOrigin.RebindSuppressed),
            result.overrides["b"],
        )
        // 'a' was a member before and after — no override needed.
        assertEquals(null, result.overrides["a"])
    }

    @Test
    fun `rebindAfterEdit=false preserves prior overrides that aren't touched by the edit`() {
        // Frame already has a User override for 'keep'; the edit drops 'a' geometrically.
        val a = media("a", 0f, 0f)
        val priorOverrides = mapOf(
            "keep" to FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
        )
        val before = frame(cx = 0f, overrides = priorOverrides)
        val after = frame(cx = 500f, overrides = priorOverrides)
        val all = listOf(before, a)
        val allAfter = listOf(after, a)

        val result = useCase.applyFrameEdit(
            frameBefore = before,
            frameAfter = after,
            allNodesBefore = all,
            allNodesAfter = allAfter,
            options = FrameEditOptions(transformContents = false, rebindAfterEdit = false),
        )

        // Both the prior override and the new suppression entry are present.
        assertEquals(
            FrameMembershipOverride(MembershipState.Included, MembershipOrigin.User),
            result.overrides["keep"],
        )
        assertEquals(
            FrameMembershipOverride(MembershipState.Included, MembershipOrigin.RebindSuppressed),
            result.overrides["a"],
        )
    }
}
