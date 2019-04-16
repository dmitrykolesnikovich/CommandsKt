package sh.ashley.commands.except

import sh.ashley.commands.ICommand
import sh.ashley.commands.IHandler
import sh.ashley.commands.IParameter

/**
 * @author ashley
 * @since 2019-04-06 23:23
 */
open class ExecutionException(message: String) : Exception(message)

class RequiredFlagsMissingException(val flags: List<IParameter>) : ExecutionException("You are missing required flags: ${flags.joinToString()}")
class UnknownFlagException(token: String, val cmd: ICommand) : Exception("Flag was not recognised: $token")
class NoApplicableHandlerException(val cmd: ICommand) : ExecutionException("Could not find a handler for your request.")
class NoCommandFound(val cmdName: String) : ExecutionException("Command was not found: $cmdName")
class HandlerExecutionException(val e: Exception,
                                val handler: IHandler): ExecutionException("An exception was thrown by the selected handler: ${e::class.qualifiedName}: ${e.message}")