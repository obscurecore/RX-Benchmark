package ru.ruslan.rx.benchmark;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import ru.ruslan.rx.benchmark.data.LongStatistics;
import ru.ruslan.rx.benchmark.data.Timing;
import ru.ruslan.rx.benchmark.data.TimingPart;
import ru.ruslan.rx.benchmark.logger.ColoredLogger;
import ru.ruslan.rx.benchmark.logger.LongStatisticsColoredFormatter;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class BootLoader {
  private final HttpClientRequest<ByteBuf, ByteBuf> clientObs;
  private final String address;
  private final boolean debugEnabled;
  private final boolean sse;

  public BootLoader(String address, boolean ignoreSSl, Map<String,String> headers, boolean debugEnabled, boolean sse) {
    this.clientObs = ClientFactory.createClient(address, ignoreSSl, headers);
    this.address = address;
    this.debugEnabled = debugEnabled;
    this.sse = sse;
  }

  public void run(int threads, int connections,
                  int requests, Duration duration) {
    final Integer maxConnections = connections < 1 ? Integer.MAX_VALUE : connections;
    final Integer requestCount = requests < 1 ? Integer.MAX_VALUE : requests;

    Scheduler scheduler;
    if (threads < 1) {
      scheduler = Schedulers.from(Executors.newFixedThreadPool(threads));
    } else {
      scheduler = Schedulers.computation();
    }

    Observable<TimingPart> timingPartObservable = Observable.range(0, requestCount)
        .flatMap(id -> measure(clientObs, scheduler, id), maxConnections);

    if (requestCount == Integer.MAX_VALUE)
      timingPartObservable = timingPartObservable.takeUntil(Observable.timer(duration.getSeconds(), TimeUnit.SECONDS));

    ColoredLogger.log(ColoredLogger.GREEN_BOLD, String.format("Running %ds test @ %s\n %d threads and %d connections",
        duration.getSeconds(),
        address,
        threads,
        connections
        ));

    timingPartObservable.toList()
        .map(this::aggregate)
        .toBlocking()
        .subscribe(res -> counting(res, duration.getSeconds()));
  }

  private Observable<TimingPart> measure(HttpClientRequest<ByteBuf, ByteBuf> clientObs, Scheduler scheduler, Integer id) {
    final TimingPart initial = TimingPart.builder().id(id).initial(true).time(System.currentTimeMillis()).build();
    return clientObs.subscribeOn(scheduler)
        .flatMap(this::mapToTiming)
        .map(clientRes -> TimingPart.builder().id(id).time(clientRes).build())
        .startWith(initial)
        .concatWith(Observable.just(1)
            .map(just -> TimingPart.builder().id(id).finished(true).time(System.currentTimeMillis()).build()))
        .onErrorReturn(err -> TimingPart.builder().id(id).failed(true).time(System.currentTimeMillis()).exception(err).build());
  }

  private  Observable<Long> mapToTiming(HttpClientResponse<ByteBuf> clientRes) {
    if (sse || (clientRes.getHeader("Content-type", "text/plain").contains("event")))
      return clientRes.getContentAsServerSentEvents()
              .map(body -> System.currentTimeMillis());
    else
      return clientRes.getContent()
              .map(body -> System.currentTimeMillis())
              .takeLast(1);
  }

  private Map<Integer, Timing> aggregate(List<TimingPart> list) {
    return list.stream()
        .collect(Collectors.groupingBy(
            TimingPart::getId,
            Collector.of(
                Timing::new,
                Timing::accept,
                Timing::combine
            ))
        );
  }

  private void counting(Map<Integer, Timing> result, long seconds) {
    LongStatistics firstAnswerStat = result.values().stream()
        .filter(res -> !res.isFailed() && res.isMeasured())
        .map(Timing::getTimings)
        .map(LongSummaryStatistics::getMin)
        .collect(
            LongStatistics::new,
            LongStatistics::accept,
            LongStatistics::combine
        );

    final Long errors = result.values().stream()
        .filter(Timing::isFailed)
        .count();

      LongStatistics answerDistanceStat = result.values().stream()
              .filter(res -> !res.isFailed() && res.isMeasured())
              .flatMap(res -> res.getDistances().stream())
              .filter(res -> res > 0.0d)
              .collect(
                      LongStatistics::new,
                      LongStatistics::accept,
                      LongStatistics::combine
              );

    LongStatistics answerStat = result.values().stream()
        .filter(res -> !res.isFailed() && res.isFinished() && res.isMeasured())
        .map(Timing::getTimings)
        .map(LongSummaryStatistics::getMax)
        .collect(
            LongStatistics::new,
            LongStatistics::accept,
            LongStatistics::combine
        );

    LongStatistics eventStat = result.values().stream()
            .filter(res -> !res.isFailed() && res.isMeasured())
            .map(Timing::eventCount)
            .collect(
                    LongStatistics::new,
                    LongStatistics::accept,
                    LongStatistics::combine
            );

    ColoredLogger.log(LongStatisticsColoredFormatter.header());
    ColoredLogger.log(LongStatisticsColoredFormatter.toString("First", firstAnswerStat));
    ColoredLogger.log(LongStatisticsColoredFormatter.toString("Distance", answerDistanceStat));
    ColoredLogger.log(LongStatisticsColoredFormatter.toString("Total", answerStat));
    ColoredLogger.log(ColoredLogger.GREEN_BOLD, "--------------------------------------------------------------------");
    ColoredLogger.log(LongStatisticsColoredFormatter.toString("Event count", eventStat));
    ColoredLogger.log(ColoredLogger.GREEN_BOLD, "Total requests sent: " + result.size());
    ColoredLogger.log(ColoredLogger.GREEN_BOLD, "Total requests finished: " + answerStat.getCount());
    ColoredLogger.log(ColoredLogger.GREEN_BOLD, "Requests per second:\t" + (double)answerStat.getCount() / seconds);
    ColoredLogger.log(ColoredLogger.RED_UNDERLINED, "Total errors: " + errors);
    if(debugEnabled) {
      result.values().stream().filter(res -> res.isFailed()).forEach(res -> System.out.println(res.getException()));
    }
  }
}
