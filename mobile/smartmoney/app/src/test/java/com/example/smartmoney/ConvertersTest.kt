package com.example.smartmoney

import com.example.smartmoney.data.local.database.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun bigDecimal_conversion_preserves_scale_and_value() {
        val original = BigDecimal("9876543210.1234")
        val stringForm = converters.fromBigDecimal(original)
        assertEquals("9876543210.1234", stringForm)

        val reconstructed = converters.toBigDecimal(stringForm)
        assertEquals(original, reconstructed)
    }

    @Test
    fun null_bigDecimal_handling() {
        assertNull(converters.fromBigDecimal(null))
        assertNull(converters.toBigDecimal(null))
    }
}
