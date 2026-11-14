package com.ozyern.exhale.ui.component

import com.composables.sfsymbols.SfSymbol
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SfNamesTest {
    @Test
    fun everySfLiteralResolves() {
        val src = File("src/main/kotlin")
        val re = Regex("""\bsf\("([^"]+)"\)""")
        val bad = src.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> re.findAll(f.readText()).map { it.groupValues[1] to f.name } }
            .filter { (n, _) -> SfSymbol.named(n) == null }
            .distinct().toList()
        assertTrue("Unknown SF symbols: $bad", bad.isEmpty())
    }
}
