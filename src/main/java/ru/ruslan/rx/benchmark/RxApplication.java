package ru.ruslan.rx.benchmark;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import lombok.SneakyThrows;
import ru.ruslan.rx.benchmark.logger.ColloredHelpFormatter;
import ru.ruslan.rx.benchmark.logger.ColoredLogger;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public class RxApplication {

  @SneakyThrows
  public static void main(String[] args) {

    OptionSet optionSet = null;

    OptionParser parser = new OptionParser();
    parser.formatHelpWith(new ColloredHelpFormatter());

    final OptionSpec<String> headerSpec = parser.accepts( "H", "Headers(could be delimited by ;)" )
        .withRequiredArg().withValuesSeparatedBy(";").ofType(String.class);
    final OptionSpec<Integer> threadSpec = parser.accepts("t", "threads")
        .withRequiredArg().ofType(Integer.class);
    final OptionSpec<Integer> connectionsSpec = parser.accepts("c", "connections")
        .withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
    final OptionSpec<Integer> maxRequestSpec = parser.accepts("r", "maxRequests")
        .withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
    final OptionSpec<String> addressSpec = parser.accepts("url", "requestURL")
        .withRequiredArg().ofType(String.class);
    final OptionSpec<String> durationSpec = parser.accepts("duration", "duration")
        .requiredUnless("r").withRequiredArg().ofType(String.class);
    final OptionSpec sslSpec = parser.accepts("k", "ignoreSSL");
    final OptionSpec sseSpec = parser.accepts("sse", "sseEnabled");
    final OptionSpec debugSpec = parser.accepts("debugEnabled", "debugEnabled");


    try {
      optionSet = parser.parse(args);
    } catch (OptionException e) {
      ColoredLogger.log(ColoredLogger.RED, e.getMessage());
      parser.printHelpOn(System.out);
      System.exit(1);
    }

    final Map<String, String> headers = optionSet.valuesOf(headerSpec).stream()
        .map(res -> res.split(":", 2))
        .collect(Collectors.toMap(res -> res[0], res -> res[1]));

    BootLoader bootLoader = new BootLoader(optionSet.valueOf(addressSpec), optionSet.has(sslSpec),
            headers, optionSet.has(debugSpec), optionSet.has(sseSpec));

    bootLoader.run(optionSet.valueOf(threadSpec), optionSet.valueOf(connectionsSpec),
        optionSet.valueOf(maxRequestSpec), Duration.parse("PT"+optionSet.valueOf(durationSpec).toUpperCase()));

  }

}
