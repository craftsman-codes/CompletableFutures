package codes.craftsman.concurrent

import io.kotlintest.fail
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class RecoveryTest : StringSpec({
  "should return result of recovery if subject failed" {
    val subject = CompletableFuture<String>()
    subject.completeExceptionally(Exception())
    val recovery = "recovery"

    val result = subject.recoverWith { CompletableFuture.completedFuture(recovery) }

    result.join() shouldBe recovery
  }

  "should return result of subject if succeeded" {
    val original = "original"
    val subject = CompletableFuture.completedFuture(original)

    val result = subject.recoverWith { CompletableFuture.completedFuture("recovery") }

    result.join() shouldBe original
  }

  "should forward exception" {
    val exception = Exception()
    val subject = CompletableFuture<String>()
    subject.completeExceptionally(exception)

    val result = subject.recoverWith { e ->
      e.shouldBeSameInstanceAs(exception)

      return@recoverWith CompletableFuture.completedFuture("recovery")
    }

    result.join()
  }
})

class ToCompletableFutureTest : StringSpec({
  val executor = Executors.newSingleThreadScheduledExecutor()

  "should return subject, if already a CompletableFuture" {
    val subject = CompletableFuture<String>()

    val result = subject.toCompletableFuture(executor)

    result.shouldBeSameInstanceAs(subject)
  }

  "should complete when subject is done" {
    val value = "test"
    val subject = mockk<Future<String>>()
    every { subject.isCancelled } returns false
    every { subject.isDone } returns true
    every { subject.get() } returns value

    val result = subject.toCompletableFuture(executor)

    result.get(100, TimeUnit.MILLISECONDS) shouldBe value
    result.isDone.shouldBeTrue()
    verify { subject.isCancelled }
    verify(exactly = 1) { subject.isDone }
    verify { subject.get() }
  }

  "should cancel when subject is cancelled" {
    val subject = mockk<Future<String>>()
    every { subject.isCancelled } returns true
    every { subject.isDone } returns true
    every { subject.cancel(true) } returns false

    val result = subject.toCompletableFuture(executor)

    shouldThrow<CancellationException> {
      result.get(100, TimeUnit.MILLISECONDS)
    }
    result.isCancelled.shouldBeTrue()
    result.isDone.shouldBeTrue()
    verify(exactly = 1) { subject.isCancelled }
    verify(exactly = 0) { subject.isDone }
    verify(exactly = 0) { subject.get() }
  }

  "should complete exceptionally when subject is completed exceptionally" {
    val subject = mockk<Future<String>>()
    val exception = Exception()
    every { subject.isCancelled } returns false
    every { subject.isDone } returns true
    every { subject.get() } throws ExecutionException(exception)

    val result = subject.toCompletableFuture(executor)

    try {
      result.get(100, TimeUnit.MILLISECONDS)
      fail("get() should not succeed")
    } catch (ee: ExecutionException) {
      ee.cause.shouldBeSameInstanceAs(exception)
    }
    result.isCancelled.shouldBeFalse()
    result.isDone.shouldBeTrue()
    verify(exactly = 1) { subject.get() }
  }

  "should try again if not done strait away" {
    val subject = mockk<Future<String>>()
    val value = "test"
    every { subject.isCancelled } returns false
    every { subject.isDone } returnsMany listOf(false, true)
    every { subject.get() } returns value

    val result = subject.toCompletableFuture(executor)

    result.get(100, TimeUnit.MILLISECONDS) shouldBe value
    verify(exactly = 2) { subject.isDone }
  }

  "should cancel underlying on complete" {
    val subject = mockk<Future<String>>()
    val scheduled = mockk<ScheduledFuture<*>>()
    val mockExecutor = mockk<ScheduledExecutorService>()
    every { subject.cancel(true) } returns true
    every { scheduled.cancel(true) } returns true
    every { subject.isCancelled } returns false
    every { subject.isDone } returns false
    every { mockExecutor.schedule(any(), any(), any()) } returns scheduled

    val value = "dummy"
    val result = subject.toCompletableFuture(mockExecutor)
    val success = result.complete(value)

    success.shouldBeTrue()
    result.get(100, TimeUnit.MILLISECONDS) shouldBe value
    verify { scheduled.cancel(true) }
    verify { subject.cancel(true) }
  }

  "should cancel underlying on completeExceptionally" {
    val subject = mockk<Future<String>>()
    val scheduled = mockk<ScheduledFuture<*>>()
    val mockExecutor = mockk<ScheduledExecutorService>()
    val exception = Exception()
    every { subject.cancel(true) } returns true
    every { scheduled.cancel(true) } returns true
    every { subject.isCancelled } returns false
    every { subject.isDone } returns false
    every { mockExecutor.schedule(any(), any(), any()) } returns scheduled

    val result = subject.toCompletableFuture(mockExecutor)
    val success = result.completeExceptionally(exception)

    success.shouldBeTrue()
    shouldThrow<ExecutionException> { result.get(100, TimeUnit.MILLISECONDS) }
    verify { scheduled.cancel(true) }
    verify { subject.cancel(true) }
  }

  "should cancel underlying on cancel" {
    val subject = mockk<Future<String>>()
    val scheduled = mockk<ScheduledFuture<*>>()
    val mockExecutor = mockk<ScheduledExecutorService>()
    every { subject.cancel(false) } returns true
    every { scheduled.cancel(true) } returns true
    every { subject.isCancelled } returns false
    every { subject.isDone } returns false
    every { mockExecutor.schedule(any(), any(), any()) } returns scheduled

    val result = subject.toCompletableFuture(mockExecutor)
    val success = result.cancel(false)

    success.shouldBeTrue()
    result.isCancelled.shouldBeTrue()
    shouldThrow<CancellationException> { result.get(100, TimeUnit.MILLISECONDS) }
    verify { scheduled.cancel(true) }
    verify { subject.cancel(false) }
  }
})
