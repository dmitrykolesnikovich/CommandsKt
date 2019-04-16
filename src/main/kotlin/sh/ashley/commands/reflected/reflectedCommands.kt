package sh.ashley.commands.reflected

import sh.ashley.commands.ICommand
import sh.ashley.commands.IHandler
import sh.ashley.commands.IParameter
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

/**
 * @author ashley
 * @since 2019-03-23 03:04
 */
class ReflectedCommand(val ref: Any) : ICommand {
    val clazz = ref::class

    override val aliases = clazz.findAnnotation<Command>()?.aliases ?: arrayOf(clazz.simpleName?.toLowerCase() ?: "")

    override val handlers: Collection<ReflectedHandler> =
        clazz.declaredMemberProperties
            .filter { it.findAnnotation<Handler>() != null }
            .map { ReflectedPropertyHandler(it, ref) }
            .union(clazz.functions.filter { it.findAnnotation<Handler>() != null }.map {
                ReflectedFunctionHandler(it, ref)
            })

    override fun toString() =
        if (aliases.isEmpty()) "(unnamed)" else aliases.joinToString()
}

abstract class ReflectedHandler(val ref: Any) : IHandler {
    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

class ReflectedFunctionHandler(val function: KFunction<*>, ref: Any) : ReflectedHandler(ref) {
    override val name = function.findAnnotation<Handler>()?.name ?: ""
    override val short = function.findAnnotation<Handler>()?.short ?: Char.MIN_VALUE
    override val parameters: Collection<ReflectedParameter> = function.parameters
        .filterNot { it.type.classifier == ref::class }.map { ReflectedParameter(this, it) }

    override fun execute(params: Map<IParameter, Any?>) {
        function.callBy(params.filter { it.key is ReflectedParameter }
            .toList()
            .union(parameters.filter { it.required || it.param.type.isMarkedNullable }
                .associate { it to null }.toList())
            .distinct()
            .distinctBy { it.first }
            .associate { (it.first as ReflectedParameter).param to it.second }.toList()
            .union(listOfNotNull((function.parameters.singleOrNull { it.kind == KParameter.Kind.INSTANCE } to ref).takeIf { it.first != null }))
            .filterNot { it.first == null }
            .toMap() as Map<KParameter, Any?>)
    }
}

class ReflectedParameter(
    override val parent: ReflectedHandler,
    val param: KParameter,
    override val required: Boolean = !(param.type.isMarkedNullable || param.isOptional)
) : IParameter {
    override val name =
        param.findAnnotation<Flag>()?.let { if (it.name.isBlank()) param.name else it.name } ?: param.name ?: ""
    override val short = param.findAnnotation<Flag>()?.short ?: Char.MIN_VALUE
    override val type = param.type.classifier!! as KClass<*>


    override fun toString() =
        if (name.isBlank()) if (short == Char.MIN_VALUE) "(unnamed)" else short.toString() else name
}

class ReflectedPropertyHandler(val property: KProperty<*>, ref: Any) : ReflectedHandler(ref) {
    override val parameters = if (property !is KMutableProperty<*>) emptyList()
    else listOf(ReflectedParameter(this, property.setter.parameters.first { it.type == property.returnType }, false))

    override fun execute(params: Map<IParameter, Any?>) {
        val single = params.toList().singleOrNull()
        if (single == null || property !is KMutableProperty<*>) {
            property.getter.callBy(
                if (property.getter.parameters.any())
                    mapOf(property.getter.parameters.first() to ref)
                else emptyMap()
            )
        } else {
//            val args = params.toList()
//                .filter { it.first is ReflectedParameter }
//                .map { (it.first as ReflectedParameter).param to it.second }
//                .union(listOfNotNull(property.setter.parameters.firstOrNull { it.type == ref::class }?.to(ref)))
//                .onEach { println("hey lol what's up ${it.first} to ${it.second}") }
//                .toMap()

            property.setter.callBy(property.setter.parameters.associateWith { if (it.kind == KParameter.Kind.INSTANCE) ref else single.second })
        }
    }

    override val name = property.findAnnotation<Handler>()?.name ?: ""
    override val short = property.findAnnotation<Handler>()?.short ?: Char.MIN_VALUE
}