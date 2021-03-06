package ru.ruslan.rx.benchmark.data;

import java.util.LongSummaryStatistics;

public class LongStatistics extends LongSummaryStatistics {

  private double sumOfSquare = 0.0d;
  private double sumOfSquareCompensation; // Low order bits of sum
  private double simpleSumOfSquare; // Used to compute right sum for non-finite inputs

  @Override
  public void accept(long value) {
    super.accept(value);
    double squareValue = value * value;
    simpleSumOfSquare += squareValue;
    sumOfSquareWithCompensation(squareValue);
  }

  public LongStatistics combine(LongStatistics other) {
    super.combine(other);
    simpleSumOfSquare += other.simpleSumOfSquare;
    sumOfSquareWithCompensation(other.sumOfSquare);
    sumOfSquareWithCompensation(other.sumOfSquareCompensation);
    return this;
  }

  private void sumOfSquareWithCompensation(double value) {
    double tmp = value - sumOfSquareCompensation;
    double velvel = sumOfSquare + tmp; // Little wolf of rounding error
    sumOfSquareCompensation = (velvel - sumOfSquare) - tmp;
    sumOfSquare = velvel;
  }

  public double getSumOfSquare() {
    double tmp =  sumOfSquare + sumOfSquareCompensation;
    if (Double.isNaN(tmp) && Double.isInfinite(simpleSumOfSquare)) {
      return simpleSumOfSquare;
    }
    return tmp;
  }

  public final double getStandardDeviation() {
    return getCount() > 0 ? Math.sqrt((getSumOfSquare() / getCount()) - Math.pow(getAverage(), 2)) : 0.0d;
  }
}
