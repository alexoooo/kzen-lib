package tech.kzen.lib.common.notation.scan

import tech.kzen.lib.common.notation.model.ProjectPath


class MultiNotationScanner(
        private val delegates: List<NotationScanner>
): NotationScanner {
    override suspend fun scan(): List<ProjectPath> {
        val all = mutableListOf<ProjectPath>()
        for (delegate in delegates) {
            all.addAll(delegate.scan())
        }
        return all
    }
}