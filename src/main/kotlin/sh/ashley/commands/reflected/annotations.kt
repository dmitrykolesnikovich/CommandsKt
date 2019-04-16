package sh.ashley.commands.reflected

import kotlin.annotation.AnnotationTarget.*

/**
 * @author ashley
 * @since 2019-03-14 17:56
 */
@Retention @Target(CLASS)
annotation class Command(vararg val aliases: String)

@Retention @Target(VALUE_PARAMETER)
annotation class Flag(val name: String = "", val short: Char = Char.MIN_VALUE)

@Retention @Target(FUNCTION, PROPERTY)
annotation class Handler(val name: String = "", val short: Char = Char.MIN_VALUE)