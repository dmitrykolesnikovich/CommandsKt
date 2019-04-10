package sh.ashley.commands

import org.apache.logging.log4j.kotlin.logger
import kotlin.reflect.KClass

/**
 * @author ashley
 * @since 2019-03-15 15:14
 */
internal val logger = logger("CommandsKt")

interface CommandDispatcher {
    fun execute(raw: String)
}

class DefaultCommandDispatcher(var prefix: String = "/") : CommandDispatcher {
    private val commandsStorage = arrayListOf<ICommand>()
    val commands
        get() = commandsStorage.toList() // immutable list. we want people to go through sanity checks to register commands.

    var onSuccess = {}
    var onException: (Exception, IHandler) -> Unit = { _, _ -> }
    var onNotPrefixed = {}
    var onNoInput = {}
    var onCommandNotFound: (String) -> Unit = {}
    var onHandlerNotFound: (ICommand) -> Unit = {}
    var onRequiredParamMissing: (List<IParameter>) -> Unit = {}

    override fun execute(raw: String) {
        if (!raw.startsWith(prefix, ignoreCase = true)) {
            this.onNotPrefixed()
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
            .ifEmpty {
                this.onCommandNotFound(cmdName)
                return
            }
            .first()

        val defaultHandler = cmd.handlers.firstOrNull { it.name == "" && it.short == Char.MIN_VALUE }

        // todo allow for combined flag input (e.g. -xvzf)
        // todo also while we're at it allow boolean flags/params to act as switches

        val matchedHandlers = parts.mapIndexedNotNullTo(arrayListOf()) { index, str ->
            str.takeIf { it.startsWith("-") }?.let {
                cmd.handlers.singleOrNull { handler -> FlagData(it).matches(handler) }
            }
        }.ifEmpty {
            listOfNotNull(defaultHandler)
        }
//            .ifEmpty {
//                this.onHandlerNotFound(cmd)
//                return
//            } todo add configuration for this behaviour

        val flags = mutableMapOf<IFlag<*>, Any?>()
        val parameters = mutableMapOf<IHandler, MutableMap<IParameter, Any?>>()
            .apply { matchedHandlers.forEach { put(it, mutableMapOf()) } }

        val iterator = parts.iterator()
        var peeked: String?
        var current = iterator.nextOrNull()

        if (null != current)
            do {
                peeked = iterator.nextOrNull()

                if (current?.startsWith('-') == true) {
                    val fd = FlagData(current)
                    if (matchedHandlers.any { fd.matches(it) }) {
                        current = peeked
                        continue
                    }

                    val matchedFlags = cmd.flags.filter { fd.matches(it) }
                    val matchedParameters = matchedHandlers.flatMap { it.parameters.filter { p -> fd.matches(p) } }

                    if (matchedFlags.isEmpty() && matchedParameters.isEmpty()) {
                        logger.debug("Discarding flag $fd (matches no parameters or flags)")
                    } else if (!matchedFlags.isEmpty() && !matchedParameters.isEmpty()) {
                        logger.debug("Discarding flag $fd (matches both parameters and flags)")
                    } else if (matchedFlags.count() > 1 || matchedParameters.count() > 1) {
                        logger.debug("Discarding flag $fd (matches multiple parameters or flags)")
                    } else {
                        val flag = matchedFlags.singleOrNull()
                        if (flag != null) {
                            if (flag.type == Boolean::class) {
                                val f = flag as IFlag<Boolean>

                                if (null == peeked)
                                    f.value = !(f.value ?: false)
                                else
                                    Boolean::class.parser?.parse(peeked)
                            } else if (null == peeked) {
                                flags += flag to null
                            } else {
                                val kc = flag.type
                                val p = kc.parser

                                if (null == p) {
                                    logger.error("Parser could not be found for type ${kc.simpleName} (flag $fd).")
                                    return
                                }

                                if (p.isValidInput(peeked)) {
                                    //flags += flag to p.parse(it)
                                    (flag as IFlag<Any>).value = p.parse(peeked)
                                    peeked = iterator.nextOrNull()
                                }
                            }
                        }

                        val param = matchedParameters.singleOrNull()
                        if (param != null) {
                            if (null == peeked) {
                                parameters.getOrPut(param.parent) { mutableMapOf() }[param] = null
                            } else if (param.type is KClass<*>) {
                                val kc = (param.type as KClass<*>)
                                val p = kc.parser

                                if (null == p) {
                                    logger.error("Parser could not be found for type ${kc.simpleName} (flag $fd).")
                                    return
                                }

                                if (p.isValidInput(peeked)) {
                                    parameters.getOrPut(param.parent) { mutableMapOf() }[param] = p.parse(peeked)
                                    peeked = iterator.nextOrNull()
                                }
                            }
                        }
                    }
                }

                current = peeked
            } while (current != null)


        parameters.ifEmpty {
            if (null == defaultHandler) {
                this.onHandlerNotFound(cmd)
                return
            } else mutableMapOf(defaultHandler to emptyMap<IParameter, Any?>())
        }.forEach { t, u ->
            val missingRequiredParams = t.parameters.filter { it.required && !u.containsKey(it) }

            if(missingRequiredParams.none()) {
                try {
                    t.execute(u)
                    this.onSuccess()
                } catch (e: Exception) {
                    this.onException(e, t)
                }
            } else {
                this.onRequiredParamMissing(missingRequiredParams)
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
        //todo make sure there aren't duplicate flag/handler names/shorts
        if (cmd.aliases.isEmpty()) {
            logger.debug { "Discarding invalid command (unnamed)" }
            return false
        }

        if (commandsStorage.filter { it.aliases.any { alias -> cmd.aliases.contains(alias) } }.any()) {
            logger.debug { "Discarding command \"${cmd.aliases.first()}\" (duplicate alias)" }
        }

        if (cmd.handlers.isEmpty() && cmd.flags.isEmpty()) {
            logger.debug { "Discarding invalid command: ${cmd.aliases.first()} (no handlers or flags)" }
            return false
        }

        cmd.handlers.filter { handler ->
            cmd.flags.any { flag ->
                (handler.short == flag.short
                        && handler.name.equals(flag.name, ignoreCase = true))
                        || handler.parameters.any { param ->
                    flag.name.equals(param.name, ignoreCase = true) && flag.short == param.short
                }
            }
        }.ifEmpty { return true }

        logger.debug { "Discarding command: ${cmd.aliases.first()} (duplicate flag/handler/parameter name or short)" }
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

    fun matches(flag: IFlag<*>) = when {
        isShort -> flag.short == name.getOrNull(0) ?: false
        else -> name.equals(flag.name, ignoreCase = true)
    }

    override fun toString() = text
}

fun <T> Iterator<T>.nextOrNull() = if (this.hasNext()) this.next() else null

internal val quoteRegex = Regex("([^\"]\\S*|\".+?\")\\s*") // thank u stack overflow