---
layout: docs
title: StateT
permalink: /docs/datatypes/statet/
---

## StateT

`StateT` also known as the `State` monad transformer allows to compute inside the context when `State` is nested in a different monad.

One issue we face with monads is that they don't compose. This can cause your code to get really hairy when trying to combine structures like `Either` and `State`. But there's a simple solution, and we're going to explain how you can use Monad Transformers to alleviate this problem.

For our purposes here, we're going to utilize a monad that serves as a container that represents branching as left or right and where state computation can be performed.

Given that both `State<S, A>` and `Either<A, B>` would be examples of datatypes that provide instances for the `Monad` typeclasses.

Because [monads don't compose](http://tonymorris.github.io/blog/posts/monads-do-not-compose/), we may end up with nested structures such as `Either<Error, State<Either<Error, State<S, Unit>>, Unit>>` when using `Either` and `State` together. Using Monad Transformers can help us to reduce this boilerplate.

In the most basic of scenarios, we'll only be dealing with one monad at a time making our lives nice and easy. However, it's not uncommon to get into scenarios where some function calls will return `Either<Error, A>`, and others will return `State<S, A>`.

So let's rewrite the example of [`State` docs]({{ '/docs/datatypes/state' | relative_url }}), but instead of representing the `Stack` as an optional `NonEmptyList` let's represent it as a `List`.

```kotlin:ank:silent
import kategory.*

typealias Stack = List<String>

fun pop(stack: Stack): Tuple2<Stack, String> = stack.first().let {
    stack.drop(1) toT it
}

fun push(s: String, stack: Stack): Tuple2<Stack, Unit> =
        listOf(s, *stack.toTypedArray()) toT Unit

fun stackOperations(stack: Stack): Tuple2<Stack, String> {
    val (s1, _) = _push("a", stack)
    val (s2, _) = _pop(s1)
    return _pop(s2)
}
```

```kotlin:ank
stackOperations(listOf("hello", "world", "!"))
```

But if we now `pop` an empty `Stack` it will result in `java.util.NoSuchElementException: List is empty.`.

```kotlin
_stackOperations(listOf()) //java.util.NoSuchElementException: List is empty.
```

Luckily Kategory offers some nice solutions [`Functional Error Handling` docs]({{ '/docs/patterns/error_handling' | relative_url }}).
Now we can model our error domain with ease.

```kotlin:ank:silent
sealed class StackError
object StackEmpty : StackError()

fun pop(stack: Stack): Either<StackError, Tuple2<Stack, String>> =
        if (stack.isEmpty()) StackEmpty.left()
        else stack.first().let {
            stack.drop(1) toT it
        }.right()

fun push(s: String, stack: Stack): Either<StackError, Tuple2<Stack, Unit>> =
        (listOf(s, *stack.toTypedArray()) toT Unit).right()

fun stackOperations(stack: Stack): Either<StackError, Tuple2<Stack, String>> {
    return push("a", stack).flatMap { (s1, _) ->
        pop(s1).flatMap { (s2, _) ->
            pop(s2)
        }
    }
}
```
```kotlin:ank
stackOperations(listOf("hello", "world", "!"))
```
```kotlin:ank
stackOperations(listOf())
```

As is immediately clear this code while properly modelling the errors has become more complex but our signature now represents a simple `Stack` as a `List` with a error domain.
Let's refactor our manual state management in the form of `(S) -> Tuple2<S, A>` to `State`.

So what we want is a return type that represents `Either` a `StackError` or a certain `State` of `Stack.` When working with `State` we don't pass around `Stack` anymore, so there is no parameter `Stack` to check anymore to check if the `Stack` is empty.

```kotlin:ank:silent
fun pop3(): Either<StackError, StateT<IdHK, Stack, String>> = TODO()
```

The only thing we can do is handle this with `StateT`. We want to wrap `State` with `Either`. Left to right wrap `State` `T` (with) `Either` `StateT<EitherHK`.
`EitherKindPartial` is an alias that helps us to fix `StackError` as the left type parameter for `Either<L, R>`.

```kotlin:ank
fun pop() = StateT<EitherKindPartial<StackError>, Stack, String> { stack ->
    if (stack.isEmpty()) StackEmpty.left()
    else stack.first().let {
        stack.drop(1) toT it
    }.right()
}

fun push2(s: String) = StateT<EitherKindPartial<StackError>, Stack, Unit> { stack ->
    (listOf(s, *stack.toTypedArray()) toT Unit).right()
}

fun stackOperations(): StateT<EitherKindPartial<StackError>, Stack, String> {
    return push2("a").flatMap({ _ ->
        pop2().flatMap({ _ ->
            pop2()
        }, Either.monad())
    }, Either.monad()).ev()
}

stackOperations().runM(listOf("hello", "world", "!"))
```
```kotlin:ank
stackOperations().runM(listOf())
```

While our code like very similar to what we had before there are some key advantages. State management is now contained within `State` and we are dealing only with 1 monad instead of 2 nested monads so we can use monad bindings!

```kotlin:ank
fun stackOperations() = StateT.monad<EitherKindPartial<StackError>, Stack>().binding {
    push2("a").bind()
    pop2().bind()
    val string = pop2().bind()
    yields(string)
}.ev()

stackOperations().runM(listOf("hello", "world", "!"))
```

```kotlin:ank
stackOperations().runM(listOf())
```

Available Instances:

```kotlin:ank
import kategory.debug.*

showInstances<StateTHK, Unit>()
```

Take a look at the [`EitherT` docs]({{ '/docs/datatypes/eithert' | relative_url }}) or [`OptionT` docs]({{ '/docs/datatypes/optiont' | relative_url }}) for an alternative version monad transformer for achieving different goals.
