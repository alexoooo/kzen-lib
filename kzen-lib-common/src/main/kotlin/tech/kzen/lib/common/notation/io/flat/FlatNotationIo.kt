package tech.kzen.lib.common.notation.io.flat

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.NotationIo
import tech.kzen.lib.common.notation.io.flat.parser.NotationParser
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia


class FlatNotationIo(
        private val source: NotationMedia,
        private val parser: NotationParser
) : NotationIo {
    override suspend fun read(location: ProjectPath): PackageNotation =
            parser.parse(source.read(location))


    override suspend fun write(location: ProjectPath, notation: PackageNotation) {
        TODO("not implemented")
    }
}
