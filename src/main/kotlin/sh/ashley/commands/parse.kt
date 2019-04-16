package sh.ashley.commands

import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.isSubclassOf

/**
 * @author ashley
 * @since 2019-03-20 19:34
 */
interface Parser<T : Any> {
    val parsedClass: KClass<T>
    fun parse(input: String): T
    fun isValidInput(input: String): Boolean
}

class EnumParser<T : Enum<*>>(override val parsedClass: KClass<T>) : Parser<T> {
    private val values = parsedClass.declaredFunctions.first { it.name == "values" }.call() as Array<T>

    override fun parse(input: String) = values.first { it.name.equals(input.trim().replace(' ', '_'), ignoreCase = true) }
    override fun isValidInput(input: String) = values.any { it.name.equals(input.trim().replace(' ', '_'), ignoreCase = true) }
}

val <T : Any> KClass<T>.parser: Parser<T>?
    get() = if (this.isSubclassOf(Enum::class))
        EnumParser(this as KClass<Enum<*>>) as Parser<T>?
    else parsers.firstOrNull { it.parsedClass == this } as Parser<T>?

val parsers = mutableListOf<Parser<*>>(
    object : Parser<Int> {
        override val parsedClass = Int::class
        override fun parse(input: String) = input.trim().toInt()
        override fun isValidInput(input: String) =
            input.count { it == '-' } <= 1 &&
                    input.trim().all { it.isDigit() || it == '-' } && input.isNotBlank()
    },
    object : Parser<Long> {
        override val parsedClass = Long::class
        override fun parse(input: String) = input.trim().toLong()
        override fun isValidInput(input: String) =
            input.count { it == '-' } <= 1 &&
                    input.trim().all { it.isDigit() || it == '-' } && input.isNotBlank()
    },
    object : Parser<String> {
        override val parsedClass = String::class
        override fun parse(input: String) = input
        override fun isValidInput(input: String) = true
    },
    object : Parser<Double> {
        override val parsedClass = Double::class
        override fun parse(input: String) = input.trim().toDouble()
        override fun isValidInput(input: String) =
            input.count { it == '-' } <= 1
                    && input.count { it == '.' } <= 1
                    && input.all { it.isDigit() || it == '.' || it == '-' }
                    && input.isNotBlank()
    },
    object : Parser<Float> {
        override val parsedClass = Float::class
        override fun parse(input: String) = input.trim().toFloat()
        override fun isValidInput(input: String) =
            input.count { it == '-' } <= 1
                    && input.count { it == '.' } <= 1
                    && input.all { it.isDigit() || it == '.' || it == '-' }
                    && input.isNotBlank()
    },
    object : Parser<Boolean> {
        private val trueValues = arrayOf("true", "yes")
        private val falseValues = arrayOf("false", "no")

        override val parsedClass = Boolean::class
        override fun parse(input: String) = input.toLowerCase().trim() in trueValues
        override fun isValidInput(input: String) = input.toLowerCase().trim() in trueValues
                || input.toLowerCase().trim() in falseValues
    }
)