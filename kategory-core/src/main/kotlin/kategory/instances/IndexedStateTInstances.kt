package kategory

@instance(IndexedStateT::class)
interface IndexedStateTFunctorInstance<F, SA, SB> : Functor<IndexedStateTKindPartial<F, SA, SB>> {

    fun FF(): Functor<F>

    override fun <A, B> map(fa: IndexedStateTKind<F, SA, SB, A>, f: (A) -> B): IndexedStateT<F, SA, SB, B> =
            fa.ev().map(f, FF())

}

//TODO review since pure only works for IndexedState<F, S, S, A> There can be no applicative for IndexedState<F, SA, SB, A>
@instance(IndexedStateT::class)
interface IndexedStateTApplicativeInstance<F, S> : IndexedStateTFunctorInstance<F, S, S>, Applicative<IndexedStateTKindPartial<F, S, S>> {

    override fun FF(): Monad<F>

    override fun <A, B> map(fa: IndexedStateTKind<F, S, S, A>, f: (A) -> B): IndexedStateT<F, S, S, B> =
            fa.ev().map(f, FF())

    override fun <A> pure(a: A): IndexedStateT<F, S, S, A> =
            IndexedStateT.pure(FF(), a)

    override fun <A, B> ap(fa: IndexedStateTKind<F, S, S, A>, ff: IndexedStateTKind<F, S, S, (A) -> B>): IndexedStateT<F, S, S, B> =
            fa.ev().ap(ff, FF())

}

@instance(IndexedStateT::class)
interface IndexedStateTMonadInstance<F, SA, SB> : IndexedStateTFunctorInstance<F, SA, SB>, Monad<IndexedStateTKindPartial<F, SA, SB>> {

    override fun FF(): Monad<F>

    override fun <A, B> map(fa: IndexedStateTKind<F, SA, SB, A>, f: (A) -> B): IndexedStateT<F, SA, SB, B> =
            fa.ev().map(f, FF())

    override fun <A, B> flatMap(fa:  IndexedStateTKind<F, SA, SB, A>, f: (A) ->  IndexedStateTKind<F, SA, SB, B>):  IndexedStateTKind<F, SA, SB, B> =
            fa.ev().flatMap(f, FF())

}

@instance(IndexedStateT::class)
interface IndexedStateTMonadStateInstance<F, S> : IndexedStateTMonadInstance<F, S, S>, MonadState<IndexedStateTKindPartial<F, S, S>, S> {

    override fun get(): IndexedStateT<F, S, S, S> = IndexedStateT.get(FF())

    override fun set(s: S):IndexedStateT<F, S, S, Unit> = IndexedStateT.set(FF(), s)
}

@instance(IndexedStateT::class)
interface IndexedStateTSemigroupKInstance<F, SA, SB> : SemigroupK<IndexedStateTKindPartial<F, SA, SB>> {

    fun FF(): Monad<F>

    fun SS(): SemigroupK<F>

    override fun <A> combineK(x: IndexedStateTKind<F, SA, SB, A>, y: IndexedStateTKind<F, SA, SB, A>): IndexedStateT<F, SA, SB, A> =
            x.ev().combineK(y, FF(), SS())

}

//TODO review since pure only works for IndexedState<F, S, S, A> There can be no MonadCombine for IndexedState<F, SA, SB, A>
@instance(IndexedStateT::class)
interface IndexedStateTMonadCombineInstance<F, S> : MonadCombine<IndexedStateTKindPartial<F, S, S>>, IndexedStateTMonadInstance<F, S, S>, IndexedStateTSemigroupKInstance<F, S, S> {

    fun MC(): MonadCombine<F>

    override fun FF(): Monad<F> = MC()

    override fun SS(): SemigroupK<F> = MC()

    override fun <A> empty(): IndexedStateTKind<F, S, S, A> = liftT(MC().empty())

    fun <A> liftT(ma: HK<F, A>): IndexedStateTKind<F, S, S, A> = IndexedStateT(FF().pure({ s: S -> FF().map(ma) { s toT it } }))
}

//TODO review since lift only works for IndexedState<F, S, S, A> There can be no MonadError for IndexedState<F, SA, SB, A>
@instance(IndexedStateT::class)
interface IndexedStateTMonadErrorInstance<F, S, E> : IndexedStateTMonadInstance<F, S, S>, MonadError<IndexedStateTKindPartial<F, S, S>, E> {

    override fun FF(): MonadError<F, E>

    override fun <A> raiseError(e: E): IndexedStateTKind<F, S, S, A> = IndexedStateT.lift(FF(), FF().raiseError(e))

    override fun <A> handleErrorWith(fa: IndexedStateTKind<F, S, S, A>, f: (E) -> IndexedStateTKind<F, S, S, A>): IndexedStateT<F, S, S, A> =
            IndexedStateT(FF().pure({ s: S -> FF().handleErrorWith(fa.runM(FF(), s), { e -> f(e).runM(FF(), s) }) }))
}