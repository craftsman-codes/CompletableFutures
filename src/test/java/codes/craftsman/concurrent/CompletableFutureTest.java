package codes.craftsman.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class CompletableFutureTest {
    {

    CompletableFutures.of(new CompletableFuture<String>(), Executors.newSingleThreadScheduledExecutor(), 10);

    }
}
