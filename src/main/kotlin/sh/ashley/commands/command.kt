package sh.ashley.commands

import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

/**
 * @author ashley
 * @since 2019-03-15 15:25
 */
interface ICommand {
    val aliases: Array<out String>
    val handlers: Collection<IHandler>
    val flags: Collection<IFlag<*>>
}

interface IHandler {
    val name: String
    val short: Char
    val parameters: Collection<IParameter>

    fun execute(params: Map<IParameter, Any?>)
}

interface IFlag<T: Any> {
    val name: String
    val short: Char
    val type: KClass<T>
    val parent: ICommand

    var value: T?
}

interface IParameter {
    val name: String
    val short: Char
    val type: KClassifier
    val required: Boolean
    val parent: IHandler
}