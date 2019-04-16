import sh.ashley.commands.dsl.command
import sh.ashley.commands.reflected.Command
import sh.ashley.commands.reflected.Flag
import sh.ashley.commands.reflected.Handler

/**
 * @author ashley
 * @since 2019-03-17 15:58
 */
@Command("piss", "pee")
object Piss {
    @Handler(short = 'z')
    var zed = 42
         set(@Flag(short = 'v') value) {
            val old = field
            field = value
            println("Hi, zed is now $field (was $old) :)")
         }

    @Handler
    fun handleCommand() {
        println("Hello from the default handler! zed=$zed")
    }

    @Handler(short = 'y')
    fun handleY(long: Long) {
        println("Hello from the y handler! zed=$zed long=$long")
    }

    @Handler(short = 'c')
    fun handleZ(@Flag(short = 'd') download: Boolean = false) {
        println("Hello from the c handler! ${if(download) "i'm downloading!!" else "i'm not downloading :("}")
    }

    @Handler(short = 'n')
    fun handleN(@Flag(short = 'i') input: String?) {
        println("Hello from the n handler! the special message is: ${input ?: "no special message :("}")
    }
}

val dslCmd = command("shid") {
    handler(short = 'h') {
        parameter<Int>(name = "long")
        parameter<String>(name = "optional-argument", isRequired = false)

        onExecute {
            val x = parameters["long"] as Int
            val stringArg = parameters["optional-argument"] as String?

            stringArg?.let {
                println("oh hello this is the string argument: $it")
            }
            println("very complicated maths: ${(x * 2)}")
        }
    }
}

@Command("valtest") object ValTestCmd {
    @Handler var shite = ""
        get() {
            println("Sup")
            return field
        }
        set(value) {
            field = value
            println("Value set to $value")
        }

    @Handler(short = 'e') var poopoo = TestEnum.ONE
        get() {
            println("Sup: $field")
            return field
        }
        set(value) {
            field = value
            println("Value set to $value")
        }
}

enum class TestEnum {
    ONE,
    TWO,
    SIXTY_NINE,
    FOUR_TWENTY
}