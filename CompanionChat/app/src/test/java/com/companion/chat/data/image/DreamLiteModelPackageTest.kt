package com.companion.chat.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DreamLiteModelPackageTest {

    @Test
    fun `目录存在但配置缺失时返回缺失项`() {
        val directory = createTempDirectory().toFile()

        val status = DreamLiteModelPackage.inspect(directory.absolutePath)

        assertEquals(
            DreamLiteModelStatus.MissingFiles(listOf(DreamLiteModelPackage.CONFIG_FILE_NAME)),
            status
        )
    }

    @Test
    fun `配置缺关键字段时返回校验错误`() {
        val directory = createTempDirectory().toFile()
        File(directory, DreamLiteModelPackage.CONFIG_FILE_NAME).writeText("""{"model_name":"dreamlite"}""")

        val status = DreamLiteModelPackage.inspect(directory.absolutePath)

        assertTrue(status is DreamLiteModelStatus.InvalidConfig)
        assertTrue((status as DreamLiteModelStatus.InvalidConfig).message.contains("runtime"))
    }
}
