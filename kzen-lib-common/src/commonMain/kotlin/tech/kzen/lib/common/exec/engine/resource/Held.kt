package tech.kzen.lib.common.exec.engine.resource

import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * A hold together with the value that carries it. [DataValue] is immutable, so adopting a value's native cannot
 * write its owners into the value the caller already has: the caller stores and passes on THIS [value] (which
 * carries [DataValue.owners]), not the one it handed in.
 */
class Held(
    val value: DataValue,
    val hold: Hold
)
