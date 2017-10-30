package kategory

typealias IndexedStateTFun<F, SA, SB, A> = (SA) -> HK<F, Tuple2<SB, A>>
typealias IndexedStateTFunKind<F, SA, SB, A> = HK<F, IndexedStateTFun<F, SA, SB, A>>

inline fun <reified F, SA, SB, A> IndexedStateTKind<F, SA, SB, A>.runM(initial: SA, MF: Monad<F> = monad()): HK<F, Tuple2<SB, A>> = (this as IndexedStateT<F, SA, SB, A>).run(initial, MF)

fun <F, SA, SB, A> IndexedStateTKind<F, SA, SB, A>.runM(MF: Monad<F>, initial: SA): HK<F, Tuple2<SB, A>> = (this as IndexedStateT<F, SA, SB, A>).run(initial, MF)


@higherkind
class IndexedStateT<F, SA, SB, A>(
        val runF: HK<F, (SA) -> HK<F, Tuple2<SB, A>>>
) : IndexedStateTKind<F, SA, SB, A> {

    companion object {
        inline operator fun <reified F, SA, SB, A> invoke(noinline run: IndexedStateTFun<F, SA, SB, A>, MF: Applicative<F> = applicative<F>()): IndexedStateT<F, SA, SB, A> = IndexedStateT(MF.pure(run))

        fun <F, SA, SB, A> invokeF(runF: IndexedStateTFunKind<F, SA, SB, A>): IndexedStateT<F, SA, SB, A> = IndexedStateT(runF)

        fun <F, S, A> lift(MF: Applicative<F>, fa: HK<F, A>): IndexedStateT<F, S, S, A> = IndexedStateT(MF.pure({ s -> MF.map(fa, { a -> Tuple2(s, a) }) }))

        fun <F, S> get(AF: Applicative<F>): IndexedStateT<F, S, S, S> = IndexedStateT(AF.pure({ s -> AF.pure(Tuple2(s, s)) }))

        fun <F, S, T> gets(AF: Applicative<F>, f: (S) -> T): IndexedStateT<F, S, S, T> = IndexedStateT(AF.pure({ s -> AF.pure(Tuple2(s, f(s))) }))

        fun <F, S> modify(AF: Applicative<F>, f: (S) -> S): IndexedStateT<F, S, S, Unit> = IndexedStateT(AF.pure({ s -> AF.map(AF.pure(f(s))) { it toT Unit } }))

        fun <F, S> modifyF(AF: Applicative<F>, f: (S) -> HK<F, S>): IndexedStateT<F, S, S, Unit> = IndexedStateT(AF.pure({ s -> AF.map(f(s)) { it toT Unit } }))

        fun <F, S> set(AF: Applicative<F>, s: S): IndexedStateT<F, S, S, Unit> = IndexedStateT(AF.pure({ _ -> AF.pure(Tuple2(s, Unit)) }))

        fun <F, S> setF(AF: Applicative<F>, s: HK<F, S>): IndexedStateT<F, S, S, Unit> = IndexedStateT(AF.pure({ _ -> AF.map(s) { Tuple2(it, Unit) } }))

        //TODO pure can only create IndexedStateT<F, S, S, A> not IndexedStateT<F, SA, SB, A>
        fun <F, S, A> pure(AF: Applicative<F>, a: A): IndexedStateT<F, S, S, A> = IndexedStateT(AF.pure({ s: S -> AF.pure(Tuple2(s, a)) }))
    }

    fun <B> map(f: (A) -> B, FF: Functor<F>): IndexedStateT<F, SA, SB, B> = transform({ (s, a) -> Tuple2(s, f(a)) }, FF)

    fun <B, SC, Z> map2(sb: IndexedStateTKind<F, SB, SC, B>, fn: (A, B) -> Z, MF: Monad<F>): IndexedStateT<F, SA, SC, Z> =
            invokeF(MF.map2(runF, sb.ev().runF) { (ssa, ssb) ->
                ssa.andThen { fsa ->
                    MF.flatMap(fsa) { (s, a) ->
                        MF.map(ssb(s)) { (s, b) -> Tuple2(s, fn(a, b)) }
                    }
                }
            })

    fun <B, SC, Z> map2Eval(sb: Eval<IndexedStateTKind<F, SB, SC, B>>, fn: (A, B) -> Z, MF: Monad<F>): Eval<IndexedStateT<F, SA, SC, Z>> =
            MF.map2Eval(runF, sb.map { it.ev().runF }) { (ssa, ssb) ->
                ssa.andThen { fsa ->
                    MF.flatMap(fsa) { (s, a) ->
                        MF.map(ssb((s))) { (s, b) -> Tuple2(s, fn(a, b)) }
                    }
                }
            }.map { IndexedStateT.invokeF(it) }

    fun <B, SC> ap(ff: IndexedStateTKind<F, SB, SC, (A) -> B>, MF: Monad<F>): IndexedStateT<F, SA, SC, B> =
            map2(ff, { a, f -> f(a) }, MF)

    fun <B, SC> product(sb: IndexedStateTKind<F, SB, SC, B>, MF: Monad<F>): IndexedStateT<F, SA, SC, Tuple2<A, B>> = map2(sb.ev(), { a, b -> Tuple2(a, b) }, MF)

    //TODO review: no way to morph `SA, SB` for that you need flatMapF
    fun <B> flatMap(fas: (A) -> IndexedStateTKind<F, SA, SB, B>, MF: Monad<F>): IndexedStateT<F, SA, SB, B> =
            invokeF(
                    MF.map(runF) { sfsa ->
                        sfsa.andThen { fsa ->
                            MF.flatMap(fsa) {
                                fas(it.b).runM(MF, it.a)
                            }
                        }
                    })

    fun <B, SC> flatMapF(fas: (A) -> IndexedStateTKind<F, SB, SC, B>, MF: Monad<F>): IndexedStateT<F, SA, SC, B> =
            invokeF(MF.map(runF) { safsba ->
                safsba.andThen { fsba ->
                    MF.flatMap(fsba) { (sb, a) ->
                        fas(a).runM(MF, sb)
                    }
                }
            })

    fun <B, SC> bimap(FF: Functor<F>, f: (SB) -> SC, g: (A) -> B): IndexedStateT<F, SA, SC, B> =
            transform({ (sb, a) -> f(sb) toT g(a) }, FF)

    /**
     * Like [map], but also allows the state `S` value to be modified.
     */
    fun <B, SC> transform(f: (Tuple2<SB, A>) -> Tuple2<SC, B>, FF: Functor<F>): IndexedStateT<F, SA, SC, B> =
            invokeF(FF.map(runF) { sfsa ->
                sfsa.andThen { fsa ->
                    FF.map(fsa) { (s, a) -> f(s toT a) }
                }
            })

    fun combineK(y: IndexedStateTKind<F, SA, SB, A>, MF: Monad<F>, SF: SemigroupK<F>): IndexedStateT<F, SA, SB, A> =
            IndexedStateT(MF.pure({ s -> SF.combineK(run(s, MF), y.ev().run(s, MF)) }))

    fun run(initial: SA, MF: Monad<F>): HK<F, Tuple2<SB, A>> = MF.flatMap(runF) { f -> f(initial) }

    fun runA(s: SA, MF: Monad<F>): HK<F, A> = MF.map(run(s, MF)) { it.b }

    fun runS(s: SA, MF: Monad<F>): HK<F, SB> = MF.map(run(s, MF)) { it.a }

}

