package com.srm.credit;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP fixture: exercises status, request count and timeouts including stalled bodies. */
final class FxHttpFixture implements AutoCloseable {
  static final String VALID =
      "{\"rate\":\"5.4321\",\"origin\":\"test-provider\",\"effectiveAt\":\"2026-10-28T14:00:00Z\"}";
  final AtomicInteger calls = new AtomicInteger();
  final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
  final Queue<Long> started = new ConcurrentLinkedQueue<>();
  final CountDownLatch release = new CountDownLatch(1);
  final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  final HttpServer server;

  FxHttpFixture() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(executor);
    server.createContext(
        "/rate",
        exchange -> {
          calls.incrementAndGet();
          started.add(System.nanoTime());
          Reply next = replies.poll();
          Reply reply = next == null ? new Reply(200, VALID, false, false) : next;
          try (exchange) {
            if (reply.stallHeaders()) {
              release.await(5, TimeUnit.SECONDS);
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            if (reply.stallBody()) {
              release.await(5, TimeUnit.SECONDS);
            }
            exchange.getResponseBody().write(bytes);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          } catch (IOException disconnectedClient) {
            /* Expected after the client's timeout. */
          }
        });
    server.start();
  }

  URI uri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/rate");
  }

  void reset() {
    calls.set(0);
    replies.clear();
    started.clear();
  }

  void enqueue(int status, String body) {
    replies.add(new Reply(status, body, false, false));
  }

  record Reply(int status, String body, boolean stallHeaders, boolean stallBody) {}

  public void close() {
    release.countDown();
    server.stop(0);
    executor.close();
  }
}
