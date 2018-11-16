package codes.craftsman.concurrent

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CompletableFutures {
  private class WrappingCompletableFuture<A>(
    private val subject: Future<A>,
    private val executor: ScheduledExecutorService,
    private val pollingInterval: Long
  ): CompletableFuture<A>() {
    @Volatile
    private var work: Future<*> = schedule(0)

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      val succeeded = cancelUnderlying(mayInterruptIfRunning)

      return super.cancel(mayInterruptIfRunning) && succeeded
    }

    override fun complete(value: A): Boolean {
      val succeeded = super.complete(value)
      cancelUnderlying(true)

      return succeeded
    }

    override fun completeExceptionally(ex: Throwable?): Boolean {
      val succeeded = super.completeExceptionally(ex)
      cancelUnderlying(true)

      return succeeded
    }

    private fun cancelUnderlying(mayInterruptIfRunning: Boolean): Boolean {
      work.cancel(true)

      return subject.cancel(mayInterruptIfRunning)
    }

    private fun tryComplete() {
      if (subject.isCancelled) {
        cancel(true)

        return
      }

      if (subject.isDone) {
        try {
          complete(subject.get())
        } catch (ee: ExecutionException) {
          completeExceptionally(ee.cause)
        } catch (ie: InterruptedException) {
          completeExceptionally(ie)
        }

        return
      }

      work = schedule(pollingInterval)
    }

    private fun schedule(delay: Long): Future<*> {
      return executor.schedule(::tryComplete, delay, TimeUnit.MILLISECONDS)
    }
  }

  companion object {
    /**
     * Creates a facade to the original future, without blocking a thread by using the provided executor to run a
     * poling function every pollingInterval ms. Tries to interrupt the original subject if completion methods like
     * CompletableFuture#cancel(Boolean) or CompletableFuture#complete(T) are called on the facade.
     * @param executor
     * The executor to schedule the polling on a Executors.newSingleThreadScheduledExecutor() could suffice.
     * @param pollingInterval
     * 10 Looks like a nice default polling interval for waiting for IO bound tasks, first polling is done strait away.
     */
    @JvmStatic
    fun <A> of(
      subject: Future<A>,
      executor: ScheduledExecutorService,
      pollingInterval: Long = 10
    ): CompletableFuture<A> =
      if (subject is CompletableFuture<A>) subject else wrap(subject, executor, pollingInterval)

    /**
     * Wraps the original Future into a new one with the recovery added. Forwards the underlying exception given by
     * {@link CompletableFuture#handle(BiFunction)} (mostly CompletionException wrapping the original cause) to the
     * recovery method if the original subject failed, else simply forwards the original subject.
     * @see CompletableFuture.handle
     */
    @JvmStatic
    fun <A> recoverWith(
      subject: CompletableFuture<A>,
      recovery: (Throwable) -> CompletableFuture<A>
    ): CompletableFuture<A> =
      subject
        .handle { value, e ->
          if (e != null) recovery(e)
          else subject
        }
        .thenCompose { x -> x }

    private fun <A> wrap(
      subject: Future<A>,
      executor: ScheduledExecutorService,
      pollingInterval: Long
    ): CompletableFuture<A> =
      WrappingCompletableFuture(subject, executor, pollingInterval)
  }
}

/**
 * @see CompletableFutures.recoverWith
 */
fun <A> CompletableFuture<A>.recoverWith(recovery: (Throwable) -> CompletableFuture<A>): CompletableFuture<A> =
  CompletableFutures.recoverWith(this, recovery)

/**
 * @see CompletableFutures.of
 */
fun <A> Future<A>.toCompletableFuture(
  executor: ScheduledExecutorService,
  pollingInterval: Long = 10
): CompletableFuture<A> =
  CompletableFutures.of(this, executor, pollingInterval)
