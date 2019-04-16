package sh.ashley.commands

import org.apache.logging.log4j.kotlin.logger
import sh.ashley.commands.except.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * @author ashley
 * @since 2019-03-15 15:14
 */
internal val logger = logger("CommandsKt")

interface CommandDispatcher {
    fun execute(raw: String)
}

open class DefaultCommandDispatcher(var prefix: String = "/") : CommandDispatcher {
    private val commandsStorage = arrayListOf<ICommand>()
    val commands
        get() = commandsStorage.toList() // immutable list. we want people to go through sanity checks to register commands.

    var onNotPrefixed: (String) -> Unit = {}
    var onNoInput = {}

    override fun execute(raw: String) {
        if (!raw.startsWith(prefix, ignoreCase = true)) {
            this.onNotPrefixed(raw)
            return
        }

        val raw = raw.substring(prefix.length)
        if (raw.isBlank()) {
            this.onNoInput()
            return
        }

        val cmdName: String
        val parts = quoteRegex.findAll(raw).map { it.value.replace("\"", "").trim() }
            .also { cmdName = it.first() }.drop(1).toMutableList()

        val cmd = commandsStorage
            .filter { it.aliases.contains(cmdName) }
            .ifEmpty { throw NoCommandFound(cmdName) }
            .first()

        // todo allow execution of handlers in order of parameters maybe? gotta think about how optional params would be handled. single - to omit??? typing null?? both????

        val matchedHandlers = parts.flatMap { str ->
            FlagData.from(str).mapNotNull {
                cmd.handlers.singleOrNull { handler -> it.matches(handler) }
            }
        }.ifEmpty {
            listOfNotNull(cmd.handlers.firstOrNull { it.name == "" && it.short == Char.MIN_VALUE })
        }.ifEmpty {
            throw NoApplicableHandlerException(cmd)
        }

        val parameters = mutableMapOf<IHandler, MutableMap<IParameter, Any?>>()
            .apply { matchedHandlers.forEach { put(it, mutableMapOf()) } }

        val paramList = matchedHandlers.flatMap { it.parameters }

        val iterator = parts.iterator()
        var peeked: String?
        var current = iterator.nextOrNull()

        if (null != current)
            do {
                peeked = iterator.nextOrNull()

                current?.let parse@{ cur ->
                    if (paramList.count() == 1) {
                        val param = paramList.single()
                        val parser = param.type.parser
                            ?: throw RuntimeException("Parser could not be found for type ${param.type.simpleName} (flag $cur).")

                        parser.takeIf { it.isValidInput(cur) }?.let {
                            parameters.put(param.parent, mutableMapOf(param to it.parse(cur)))
                            peeked = iterator.nextOrNull()
                            return@parse
                        }
                    }

                    if (cur.startsWith('-')) {
                        val fd2 = FlagData.from(cur)

                        fd2.forEachIndexed loop@{ index, fd ->
                            if (matchedHandlers.any { fd.matches(it) }) {
                                return@loop
                            }

                            val isLast = index == fd2.withIndex().last().index
                            val matchedParameters = matchedHandlers.flatMap { it.parameters.filter { p -> fd.matches(p) } }

                            when {
                                matchedParameters.isEmpty() -> throw UnknownFlagException(fd.toString(), cmd)
                                matchedParameters.count() > 1 -> logger.debug("Discarding flag $fd (matches multiple parameters)")
                                else -> matchedParameters.singleOrNull()?.also { param ->
                                    if (param.type == Boolean::class) {
                                        if (null == peeked) {
                                            parameters.getOrPut(param.parent) { mutableMapOf() }[param] = true
                                            current = peeked
                                            return@loop
                                        }
                                        // todo parse non-null next token if boolean and if it's invalid and starts with - then default to true
                                    }

                                    val x = peeked // thanks kotlin
                                    if (null == x) {
                                        if (!param.required)
                                            parameters.getOrPut(param.parent) { mutableMapOf() }[param] = null
                                    } else if (isLast) {
                                        val p = param.type.parser
                                            ?: throw RuntimeException("Parser could not be found for type ${param.type.simpleName} (flag $fd).")

                                        if (p.isValidInput(x)) {
                                            parameters.getOrPut(param.parent) { mutableMapOf() }[param] = p.parse(x)
                                            peeked = iterator.nextOrNull()
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
                current = peeked
            } while (current != null)


        parameters.forEach { t, u ->
            val missingRequiredParams = t.parameters.filter { it.required && !u.containsKey(it) }

            if (missingRequiredParams.none()) {
                try {
                    t.execute(u)
                } catch (e: Exception) {
                    throw HandlerExecutionException(e, t)
                }
            } else {
                throw RequiredFlagsMissingException(missingRequiredParams)
            }
        }
    }

    fun registerCommand(cmd: ICommand): Boolean {
        if (!isCommandValid(cmd))
            return false

        commandsStorage += cmd
        return true
    }

    fun unregisterCommand(cmd: ICommand) {
        if (commandsStorage.contains(cmd))
            commandsStorage.remove(cmd)
    }

    private fun isCommandValid(cmd: ICommand): Boolean {
        if (cmd.aliases.isEmpty()) {
            logger.debug { "Discarding invalid command (unnamed)" }
            return false
        }

        if (commandsStorage.filter { it.aliases.any { alias -> cmd.aliases.contains(alias) } }.any()) {
            logger.debug { "Discarding command \"${cmd.aliases.first()}\" (duplicate alias)" }
        }

        if (cmd.handlers.isEmpty()) {
            logger.debug { "Discarding invalid command: ${cmd.aliases.first()} (no handlers)" }
            return false
        }

        cmd.handlers.filter { handler ->
            cmd.handlers.count { it.name == handler.name && it.short == handler.short } > 1
                    || handler.parameters.filter { param -> handler.parameters.count { param.name == it.name && param.short == it.short } > 1 }.count() > 1
        }.ifEmpty { return true }

        logger.debug { "Discarding command: ${cmd.aliases.first()} (duplicate handler/parameter name or short)" }
        return false
    }
}

internal data class FlagData(private val text: String) {
    val isShort: Boolean
        get() = text.startsWith('-') && !text.startsWith("--")

    val name: String
        get() = if (isShort) text.substring(1) else text.substring(2)

    fun matches(param: IParameter) = when {
        isShort -> param.short == name.getOrNull(0) ?: false
        else -> name.equals(param.name, ignoreCase = true)
    }

    fun matches(handler: IHandler) = when {
        isShort -> handler.short == name.getOrNull(0) ?: false
        else -> name.equals(handler.name, ignoreCase = true)
    }

    override fun toString() = text

    companion object {
        fun from(str: String): List<FlagData> {
            return if (str.startsWith("--"))
                listOf(FlagData(str))
            else if (str.startsWith("-"))
                if (str.count() == 2) {
                    listOf(FlagData(str))
                } else {
                    str.drop(1).map { FlagData("-$it") }
                }
            else emptyList()
        }
    }
}

fun <T> Iterator<T>.nextOrNull() = if (this.hasNext()) this.next() else null

internal val quoteRegex = Regex("([^\"]\\S*|\".+?\")\\s*") // thank u stack overflow