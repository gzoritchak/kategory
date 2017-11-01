package kategory

typealias StateTFun<F, S, A> = (S) -> HK<F, Tuple2<S, A>>
typealias StateTFunKind<F, S, A> = HK<F, StateTFun<F, S, A>>

inline fun <reified F, S, A> StateTKind<F, S, A>.runM(initial: S, MF: Monad<F> = monad()): HK<F, Tuple2<S, A>> = (this as StateT<F, S, A>).run(initial, MF)

fun <F, S, A> StateTKind<F, S, A>.runM(MF: Monad<F>, initial: S): HK<F, Tuple2<S, A>> = (this as StateT<F, S, A>).run(initial, MF)

@higherkind
class StateT<F, S, A>(
        val runF: StateTFunKind<F, S, A>
) : StateTKind<F, S, A> {

    companion object {
        inline operator fun <reified F, S, A> invoke(noinline run: StateTFun<F, S, A>, MF: Monad<F> = monad<F>()): StateT<F, S, A> = StateT(MF.pure(run))

        inline operator fun <reified F, S, A> invoke(MF: Monad<F> = monad<F>(), noinline run: StateTFun<F, S, A>): StateT<F, S, A> = StateT(MF.pure(run))

        fun <F, S, A> invokeF(runF: StateTFunKind<F, S, A>): StateT<F, S, A> = StateT(runF)

        fun <F, S, A> lift(MF: Monad<F>, fa: HK<F, A>): StateT<F, S, A> = StateT(MF.pure({ s -> MF.map(fa, { a -> Tuple2(s, a) }) }))

        fun <F, S> get(AF: Applicative<F>): StateT<F, S, S> = StateT(AF.pure({ s -> AF.pure(Tuple2(s, s)) }))

        fun <F, S, T> gets(AF: Applicative<F>, f: (S) -> T): StateT<F, S, T> = StateT(AF.pure({ s -> AF.pure(Tuple2(s, f(s))) }))

        fun <F, S> modify(AF: Applicative<F>, f: (S) -> S): StateT<F, S, Unit> = StateT(AF.pure({ s -> AF.map(AF.pure(f(s))) { it toT Unit } }))

        fun <F, S> modifyF(AF: Applicative<F>, f: (S) -> HK<F, S>): StateT<F, S, Unit> = StateT(AF.pure({ s -> AF.map(f(s)) { it toT Unit } }))

        fun <F, S> set(AF: Applicative<F>, s: S): StateT<F, S, Unit> = StateT(AF.pure({ _ -> AF.pure(Tuple2(s, Unit)) }))

        fun <F, S> setF(AF: Applicative<F>, s: HK<F, S>): StateT<F, S, Unit> = StateT(AF.pure({ _ -> AF.map(s) { Tuple2(it, Unit) } }))

        fun <F, S, A, B> tailRecM(a: A, f: (A) -> HK<StateTKindPartial<F, S>, Either<A, B>>, MF: Monad<F>): StateT<F, S, B> =
                StateT(MF.pure({ s: S ->
                    MF.tailRecM(Tuple2(s, a), { (s, a0) ->
                        MF.map(f(a0).runM(MF, s)) { (s, ab) ->
                            ab.bimap({ a1 -> Tuple2(s, a1) }, { b -> Tuple2(s, b) })
                        }
                    })
                }))
    }

    fun <B> map(f: (A) -> B, FF: Functor<F>): StateT<F, S, B> = transform({ (s, a) -> Tuple2(s, f(a)) }, FF)

    fun <B, Z> map2(sb: StateT<F, S, B>, fn: (A, B) -> Z, MF: Monad<F>): StateT<F, S, Z> =
            invokeF(MF.map2(runF, sb.runF) { (ssa, ssb) ->
                ssa.andThen { fsa ->
                    MF.flatMap(fsa) { (s, a) ->
                        MF.map(ssb(s)) { (s, b) -> Tuple2(s, fn(a, b)) }
                    }
                }
            })

    fun <B, Z> map2Eval(sb: Eval<StateT<F, S, B>>, fn: (A, B) -> Z, MF: Monad<F>): Eval<StateT<F, S, Z>> =
            MF.map2Eval(runF, sb.map { it.runF }) { (ssa, ssb) ->
                ssa.andThen { fsa ->
                    MF.flatMap(fsa) { (s, a) ->
                        MF.map(ssb((s))) { (s, b) -> Tuple2(s, fn(a, b)) }
                    }
                }
            }.map { invokeF(it) }

    fun <B> ap(ff: StateTKind<F, S, (A) -> B>, MF: Monad<F>): StateT<F, S, B> =
            ff.ev().map2(this, { f, a -> f(a) }, MF)

    fun <B> product(sb: StateT<F, S, B>, MF: Monad<F>): StateT<F, S, Tuple2<A, B>> = map2(sb, { a, b -> Tuple2(a, b) }, MF)

    fun <B> flatMap(fas: (A) -> StateTKind<F, S, B>, MF: Monad<F>): StateT<F, S, B> =
            invokeF(
                    MF.map(runF) { sfsa ->
                        sfsa.andThen { fsa ->
                            MF.flatMap(fsa) {
                                fas(it.b).runM(MF, it.a)
                            }
                        }
                    })

    fun <B> flatMapF(faf: (A) -> HK<F, B>, MF: Monad<F>): StateT<F, S, B> =
            invokeF(
                    MF.map(runF) { sfsa ->
                        sfsa.andThen { fsa ->
                            MF.flatMap(fsa) { (s, a) ->
                                MF.map(faf(a)) { b -> Tuple2(s, b) }
                            }
                        }
                    })

    fun <B> transform(f: (Tuple2<S, A>) -> Tuple2<S, B>, FF: Functor<F>): StateT<F, S, B> =
            invokeF(
                    FF.map(runF) { sfsa ->
                        sfsa.andThen { fsa ->
                            FF.map(fsa, f)
                        }
                    })

    fun combineK(y: StateTKind<F, S, A>, MF: Monad<F>, SF: SemigroupK<F>): StateT<F, S, A> =
            StateT(MF.pure({ s -> SF.combineK(run(s, MF), y.ev().run(s, MF)) }))

    fun run(initial: S, MF: Monad<F>): HK<F, Tuple2<S, A>> = MF.flatMap(runF) { f -> f(initial) }

    fun runA(s: S, MF: Monad<F>): HK<F, A> = MF.map(run(s, MF)) { it.b }

    fun runS(s: S, MF: Monad<F>): HK<F, S> = MF.map(run(s, MF)) { it.a }
}

inline fun <reified F, S, A> StateTFunKind<F, S, A>.stateT(MF: Monad<F> = monad()): StateT<F, S, A> = StateT(this)

inline fun <reified F, S, A> StateTFun<F, S, A>.stateT(MF: Monad<F> = monad()): StateT<F, S, A> = StateT(this, MF)

inline fun <reified F, S, A> StateT.Companion.lift(fa: HK<F, A>, MF: Monad<F> = monad<F>()): StateT<F, S, A> = StateT(MF.pure({ s -> MF.map(fa, { a -> Tuple2(s, a) }) }))

inline fun <reified F, S> StateT.Companion.get(AF: Applicative<F> = applicative<F>(), dummy: Unit = Unit): StateT<F, S, S> = StateT(AF.pure({ s: S -> AF.pure(Tuple2(s, s)) }))

inline fun <reified F, S, T> StateT.Companion.gets(AF: Applicative<F> = applicative<F>(), dummy: Unit = Unit, crossinline f: (S) -> T): StateT<F, S, T> = StateT(AF.pure({ s: S -> AF.pure(Tuple2(s, f(s))) }))

inline fun <reified F, S> StateT.Companion.set(s: S, AF: Applicative<F> = applicative<F>()): StateT<F, S, Unit> = StateT(AF.pure({ _: S -> AF.pure(Tuple2(s, Unit)) }))

inline fun <reified F, S> StateT.Companion.modify(AF: Applicative<F> = applicative<F>(), dummy: Unit = Unit, crossinline f: (S) -> HK<F, S>): StateT<F, S, Unit> = StateT(AF.pure({ s -> AF.map(f(s)) { it toT Unit } }))

