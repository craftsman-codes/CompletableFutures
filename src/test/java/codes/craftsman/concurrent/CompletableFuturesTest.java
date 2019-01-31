package codes.craftsman.concurrent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

class CompletableFuturesTest {
  @Test
  void ShouldAllowStaticAccessToOf() {
    Assertions.assertNotNull(
      CompletableFutures.of(
        CompletableFuture.completedFuture("test"),
        Executors.newSingleThreadScheduledExecutor()
      )
    );
  }

  @Test
  void ShouldAllowStaticAccessToRecoveryWith() {
    final CompletableFuture<String> future = CompletableFuture.completedFuture("test");

    Assertions.assertNotNull(
      CompletableFutures.recoverWith(future, ignored -> future)
    );
  }
}
