package tech.kzen.lib.common.notation.read.flat

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.NotationReader
import tech.kzen.lib.common.notation.read.flat.parser.NotationParser
import tech.kzen.lib.common.notation.read.flat.source.NotationSource


class FlatNotationReader(
        private val source: NotationSource,
        private val parser: NotationParser
) : NotationReader {
    override suspend fun read(location: ProjectPath): PackageNotation =
            parser.parse(source.read(location))
}
