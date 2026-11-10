package com.mutinymedia.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterModelTest {
    @Test
    fun onlyListsFreeModelsWithExplicitZeroPrices() {
        val catalog = JSONObject(
            """
            {"data": [
                {"id":"provider/free:free","name":"Free","pricing":{"prompt":"0.0","completion":"0"}},
                {"id":"provider/free:free","name":"Free","pricing":{"prompt":"0","completion":"0"}},
                {"id":"provider/paid","pricing":{"prompt":"0","completion":"0"}},
                {"id":"provider/not-free:free","pricing":{"prompt":"0","completion":"0.000001"}},
                {"id":"provider/unknown:free","pricing":{"prompt":"0"}},
                {"id":"openrouter/free","pricing":{"prompt":"0","completion":"0"}}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("provider/free:free", "openrouter/free"), parseFreeModels(catalog).map { it.id })
    }
}
