package sh.ashley.commands.dsl

import sh.ashley.commands.ICommand
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

    fun handler(name: String = "", short: Char = Char.MIN_VALUE, block: DslHandler.() -> Unit) {
        val handler = DslHandler(this, name, short)
        handler.block()
        handlers += handler
    }

    override fun toString() =
        if (aliases.isEmpty()) "(unnamed)" else aliases.joinToString()
}

@CommandsDslMarker
class DslHandler internal constructor(
    private val cmd: DslCommand,
    override val name: String = "",
    override val short: Char = Char.MIN_VALUE
) : IHandler {
    override val parameters = arrayListOf<DslParameter<*>>()
    private var executionCallback: DslExecutionContext.() -> Unit = {}

    inline fun <reified T: Any> parameter(name: String = "", short: Char = Char.MIN_VALUE, isRequired: Boolean = true): DslParameter<T> {
        val x = DslParameter(name, short, T::class, isRequired, this)
        parameters += x
        return x
    }

    fun onExecute(callback: DslExecutionContext.() -> Unit) {
        executionCallback = callback
    }

    override fun execute(params: Map<IParameter, Any?>) {
        val ctx = DslExecutionContext(cmd, params)
        executionCallback.invoke(ctx)
    }

    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

@CommandsDslMarker
class DslParameter<T: Any>(
    override val name: String = "",
    override val short: Char = Char.MIN_VALUE,
    override val type: KClass<T>,
    override val required: Boolean,
    override val parent: IHandler
) : IParameter {
    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

@CommandsDslMarker
class DslExecutionContext internal constructor(cmd: DslCommand, private val params: Map<IParameter, Any?>) {
    val parameters = DslParameterStore(params)

    fun <T: Any> DslParameter<T>.value(): T? {
        return this@DslExecutionContext.params.getOrDefault(this, null) as T?
    }
}

class DslParameterStore internal constructor(val params: Map<IParameter, Any?>) {
    operator fun get(flagName: String): Any? =
        params.filter { it.key.name.equals(flagName, ignoreCase = true) }.ifEmpty { null }?.values?.first()

    operator fun get(flagShort: Char): Any? =
        params.filter { it.key.short.equals(flagShort, ignoreCase = true) }.ifEmpty { null }?.values?.first()
}

@DslMarker
annotation class CommandsDslMarker