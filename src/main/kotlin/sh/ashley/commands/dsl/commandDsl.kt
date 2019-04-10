package sh.ashley.commands.dsl

import sh.ashley.commands.ICommand
import sh.ashley.commands.IFlag
import sh.ashley.commands.IHandler
import sh.ashley.commands.IParameter
import kotlin.reflect.KClass

/**
 * @author ashley
 * @since 2019-03-23 03:08
 */
fun command(vararg aliases: String, block: DslCommand.() -> Unit): DslCommand {
    val dslCommand = DslCommand(aliases)
    dslCommand.block()
    return dslCommand
}

@CommandsDslMarker
class DslCommand internal constructor(override val aliases: Array<out String>) : ICommand {
    override val handlers = arrayListOf<DslHandler>()
    override val flags = arrayListOf<DslFlag<*>>()

    inline fun <reified T: Any> flag(value: T, name: String = "", short: Char = Char.MIN_VALUE) {
        flags += DslFlag(name, short, T::class, value, this)
    }

    fun handler(name: String = "", short: Char = Char.MIN_VALUE, block: DslHandler.() -> Unit) {
        val handler = DslHandler(this, name, short)
        handler.block()
        handlers += handler
    }
}

@CommandsDslMarker
class DslHandler internal constructor(
    private val cmd: DslCommand,
    override val name: String = "",
    override val short: Char = Char.MIN_VALUE
) : IHandler {
    override val parameters = arrayListOf<DslParameter>()
    private var executionCallback: DslExecutionContext.() -> Unit = {}

    inline fun <reified T> parameter(name: String = "", short: Char = Char.MIN_VALUE, isRequired: Boolean = true) {
        parameters += DslParameter(name, short, T::class, isRequired, this)
    }

    fun onExecute(callback: DslExecutionContext.() -> Unit) {
        executionCallback = callback
    }

    override fun execute(params: Map<IParameter, Any?>) {
        val ctx = DslExecutionContext(cmd, params)
        executionCallback.invoke(ctx)
    }
}

@CommandsDslMarker
class DslParameter(
    override val name: String = "",
    override val short: Char = Char.MIN_VALUE,
    override val type: KClass<*>,
    override val required: Boolean,
    override val parent: IHandler
) : IParameter {
    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

@CommandsDslMarker
class DslFlag<T : Any>(
    override val name: String,
    override val short: Char,
    override val type: KClass<T>,
    override var value: T? = null,
    override val parent: ICommand
) : IFlag<T>

@CommandsDslMarker
class DslExecutionContext internal constructor(cmd: DslCommand, params: Map<IParameter, Any?>) {
    val parameters = DslParameterStore(params)
    val flags = DslFlagStore(cmd.flags)
}

class DslParameterStore internal constructor(val params: Map<IParameter, Any?>) {
    operator fun get(flagName: String): Any? =
        params.filter { it.key.name.equals(flagName, ignoreCase = true) }.ifEmpty { null }?.values?.first()

    operator fun get(flagShort: Char): Any? =
        params.filter { it.key.short.equals(flagShort, ignoreCase = true) }.ifEmpty { null }?.values?.first()
}

class DslFlagStore internal constructor(val params: Collection<IFlag<*>>) {
    operator fun get(flagName: String): Any? =
        params.filter { it.name.equals(flagName, ignoreCase = true) }.ifEmpty { null }?.first()?.value

    operator fun get(flagShort: Char): Any? =
        params.filter { it.short.equals(flagShort, ignoreCase = true) }.ifEmpty { null }?.first()?.value
}

@DslMarker
annotation class CommandsDslMarker