package eu.kanade.tachiyomi.util.view

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroupEdgesTest {

    private val header = mockk<IFlexible<*>>()
    private val rows = List(3) { mockk<IFlexible<*>>() }

    private fun adapterOf(vararg items: IFlexible<*>): FlexibleAdapter<IFlexible<*>> {
        val adapter = mockk<FlexibleAdapter<IFlexible<*>>>()
        every { adapter.getItem(any()) } answers { items.getOrNull(firstArg<Int>()) }
        return adapter
    }

    private fun edges(adapter: FlexibleAdapter<IFlexible<*>>, position: Int) =
        groupEdges(adapter, position) { it in rows }

    @Test
    fun `a lone row is both the top and the bottom of its group`() {
        val adapter = adapterOf(header, rows[0], header)

        assertEquals(true to true, edges(adapter, 1))
    }

    @Test
    fun `the first row of a run is only the top`() {
        val adapter = adapterOf(header, rows[0], rows[1], rows[2])

        assertEquals(true to false, edges(adapter, 1))
    }

    @Test
    fun `a row in the middle of a run is neither edge`() {
        val adapter = adapterOf(header, rows[0], rows[1], rows[2])

        assertEquals(false to false, edges(adapter, 2))
    }

    @Test
    fun `the last row of a run is only the bottom`() {
        val adapter = adapterOf(header, rows[0], rows[1], rows[2])

        assertEquals(false to true, edges(adapter, 3))
    }

    @Test
    fun `rows at the start and end of the adapter count as edges`() {
        val adapter = adapterOf(rows[0], rows[1])

        assertEquals(true to false, edges(adapter, 0))
        assertEquals(false to true, edges(adapter, 1))
    }
}
