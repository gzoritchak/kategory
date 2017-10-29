package kategory

import io.kotlintest.KTestJUnitRunner
import io.kotlintest.matchers.shouldNotBe
import org.junit.runner.RunWith

@RunWith(KTestJUnitRunner::class)
class StateTTests : UnitSpec() {

    val M: StateTMonadStateInstance<TryHK, Int> = StateT.monadState<TryHK, Int>(Try.monad())

    val EQ: Eq<StateTKind<TryHK, Int, Int>> = Eq { a, b ->
        a.runM(1) == b.runM(1)
    }

    val EQ_UNIT: Eq<StateTKind<TryHK, Int, Unit>> = Eq { a, b ->
        a.runM(1) == b.runM(1)
    }

    val EQ_LIST: Eq<HK<StateTKindPartial<ListKWHK, Int>, Int>> = Eq { a, b ->
        a.runM(1) == b.runM(1)
    }

    init {

        "instances can be resolved implicitly" {
            functor<StateTKindPartial<IdHK, Int>>() shouldNotBe null
            applicative<StateTKindPartial<IdHK, Int>>() shouldNotBe null
            monad<StateTKindPartial<IdHK, Int>>() shouldNotBe null
            monadState<StateTKindPartial<IdHK, Int>, Int>() shouldNotBe null
            semigroupK<StateTKindPartial<NonEmptyListHK, NonEmptyListHK>>() shouldNotBe null
        }

        testLaws(MonadStateLaws.laws(M, EQ, EQ_UNIT))
        testLaws(SemigroupKLaws.laws(
                StateT.semigroupK<ListKWHK, Int>(ListKW.monad(), ListKW.semigroupK()),
                StateT.applicative<ListKWHK, Int>(ListKW.monad()),
                EQ_LIST))
        testLaws(MonadCombineLaws.laws(StateT.monadCombine<ListKWHK, Int>(ListKW.monadCombine()),
                { StateT.lift(ListKW.monad(), ListKW.pure(it)) },
                { StateT.lift(ListKW.monad(), ListKW.pure({ s: Int -> s * 2 })) },
                EQ_LIST))

    }
}
