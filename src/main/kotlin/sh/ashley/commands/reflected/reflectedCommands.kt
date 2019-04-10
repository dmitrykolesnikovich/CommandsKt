package sh.ashley.commands.reflected

import sh.ashley.commands.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * @author ashley
 * @since 2019-03-23 03:04
 */
class ReflectedCommand(val o: Any) : ICommand {
    val clazz = o::class

    override val aliases = clazz.findAnnotation<Command>()?.aliases ?: arrayOf(clazz.simpleName?.toLowerCase() ?: "")
    override val handlers: Collection<ReflectedHandler> =
        clazz.functions.filter { it.findAnnotation<Handler>() != null }.map { ReflectedHandler(it, o) }
    override val flags: Collection<ReflectedFlag<*>> = clazz.declaredMemberProperties.filter {
        it is KMutableProperty<*> && it.findAnnotation<Flag>() != null
    }.map { ReflectedFlag(this, it as KMutableProperty<Any>, o) }

    override fun toString() =
        if(aliases.isEmpty()) "(unnamed)" else aliases.joinToString()
}

class ReflectedHandler(val function: KFunction<*>, val o: Any) : IHandler {
    override val name = function.findAnnotation<Handler>()?.name ?: ""
    override val short = function.findAnnotation<Handler>()?.short ?: Char.MIN_VALUE
    override val parameters: Collection<ReflectedParameter> = function.parameters
        .filterNot { it.type.classifier == o::class }.map { ReflectedParameter(this, it) }

    override fun execute(params: Map<IParameter, Any?>) {
        val argsList = params.filter { it.key is ReflectedParameter }
            .map { (it.key as ReflectedParameter).param to it.value }
            .toMutableList()
        function.parameters.firstOrNull { it.type.classifier == o::class }?.let { argsList.add(it to o) }
        function.callBy(argsList.toMap())
    }

    override fun toString() =
        if(name.isBlank()) if(short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

class ReflectedFlag<T: Any>(override val parent: ICommand, val property: KMutableProperty<T>, val o: Any) : IFlag<T> {
    override val name = property.findAnnotation<Flag>()?.name ?: property.name
    override val short = property.findAnnotation<Flag>()?.short ?: Char.MIN_VALUE
    override val type = property.returnType.classifier!! as KClass<T>
    override var value: T?
        get() = property.getter.call(o)
        set(value) = property.setter.call(o, value)

    override fun toString() =
        if(name.isBlank()) if(short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

class ReflectedParameter(override val parent: ReflectedHandler, val param: KParameter) : IParameter {
    override val name =
        param.findAnnotation<Flag>()?.let { if (it.name.isBlank()) param.name else it.name } ?: param.name ?: ""
    override val short = param.findAnnotation<Flag>()?.short ?: Char.MIN_VALUE
    override val type = param.type.classifier!!
    override val required = !(param.type.isMarkedNullable || param.isOptional)

    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}