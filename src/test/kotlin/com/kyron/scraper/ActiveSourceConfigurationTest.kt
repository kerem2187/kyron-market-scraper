package com.kyron.scraper

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ActiveSourceConfigurationTest {
    private fun read(path: String) = Files.readString(Path.of(path))

    @Test fun productionDefaultsContainOnlyApprovedSources() {
        val workflow = read(".github/workflows/scrape.yml")
        assertTrue(workflow.contains("default: \"macrocenter,migros,sok,off\""))
        assertTrue(workflow.contains("github.event.inputs.chains || 'macrocenter,migros,sok,off'"))
        assertFalse(workflow.contains("'a101'"))
        assertFalse(workflow.contains("'carrefoursa'"))
    }

    @Test fun liveMatrixContainsOnlyApprovedSources() {
        val workflow = read(".github/workflows/source-smoke.yml")
        assertTrue(workflow.contains("source: [macrocenter, migros, sok, off]"))
        assertFalse(workflow.contains("a101"))
        assertFalse(workflow.contains("carrefoursa"))
    }

    @Test fun runnerRejectsExcludedSourceNames() {
        val runner = read(".github/scripts/run-sources.sh")
        assertTrue(runner.contains("macrocenter|migros|sok|off|all)"))
        assertFalse(runner.contains("|a101|"))
        assertFalse(runner.contains("|carrefoursa|"))
    }
}
