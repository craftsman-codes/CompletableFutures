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
    @JvmStatic
    fun <A> of(
      subject: Future<A>,
      executor: ScheduledExecutorService,
      pollingInterval: Long = 10
    ): CompletableFuture<A> =
      if (subject is CompletableFuture<A>) subject else wrap(subject, executor, pollingInterval)

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

fun <A> CompletableFuture<A>.recoverWith(recovery: (Throwable) -> CompletableFuture<A>): CompletableFuture<A> =
  CompletableFutures.recoverWith(this, recovery)

fun <A> Future<A>.toCompletableFuture(
  executor: ScheduledExecutorService,
  pollingInterval: Long = 10
): CompletableFuture<A> =
  CompletableFutures.of(this, executor, pollingInterval)
