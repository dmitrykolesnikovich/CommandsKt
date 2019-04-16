package sh.ashley.commands

import kotlin.reflect.KClass

/**
 * @author ashley
 * @since 2019-03-15 15:25
 */
interface ICommand {
    val aliases: Array<out String>
    val handlers: Collection<IHandler>
}

interface IHandler : Named {
    val parameters: Collection<IParameter>

    fun execute(params: Map<IParameter, Any?>)
}

interface IParameter : Named {
    val type: KClass<*>
    val required: Boolean
    val parent: IHandler
}

interface Named {
    val name: String
    val short: Char
}