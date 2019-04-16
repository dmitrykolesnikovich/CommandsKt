import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import sh.ashley.commands.DefaultCommandDispatcher
import sh.ashley.commands.reflected.ReflectedCommand

object TestClass {
    lateinit var dispatcher: DefaultCommandDispatcher
        private set

    @BeforeTest
    fun setupDispatcher() {
        dispatcher = DefaultCommandDispatcher(prefix = "").apply {
            this.registerCommand(ReflectedCommand(Piss))
            this.registerCommand(dslCmd)
        }
    }

    @Test
    fun testExecution() {
        dispatcher.execute("pee")
    }

    @Test
    fun testExecutionHandlerNoParams() {
        dispatcher.execute("pee -y")
    }

    @Test
    fun testExecutionHandlerBooleanParam() {
        dispatcher.execute("pee -d -c")
    }

    @Test
    fun testExecutionHandlerOneParam() {
        dispatcher.execute("pee -z 5")
    }

    @Test
    fun testExecutionNegativeParameter() {
        dispatcher.execute("piss -y --long -10")
    }

    @Test
    fun testExecutionNonexistentParameter() {
        dispatcher.execute("piss -y -g")
    }

    @Test
    fun testDslCommandExecution() {
        dispatcher.execute("shid -h --bigboy 10 --long 5")
    }

    @Test
    fun testDslCommandExecutionOptionalParams() {
        dispatcher.execute("shid -h -b 3 --long 6 --optional-argument \"very wordy argument\"")
    }

    @Test
    fun testNullableParameters() {
        dispatcher.execute("pee -n")
    }

    @Test
    fun testNullableParameters2() {
        dispatcher.execute("pee -n -i \"Special Message here, eat your vegetables\"")
    }

    @Test
    fun testCombinedHandlers() {
        dispatcher.execute("pee -ncd")
    }
}