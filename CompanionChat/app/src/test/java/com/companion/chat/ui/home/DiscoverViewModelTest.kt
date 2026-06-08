package com.companion.chat.ui.home

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiscoverViewModelTest {

    @Test
    fun `暴露 AndroidViewModelFactory 需要的 Application 构造函数`() {
        val constructor = DiscoverViewModel::class.java.getConstructor(Application::class.java)

        assertNotNull(constructor)
    }
}
