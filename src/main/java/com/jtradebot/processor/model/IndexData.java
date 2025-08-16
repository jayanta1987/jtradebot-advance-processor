package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.indicator.Resistance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ta4j.core.BarSeries;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder(toBuilder = true)
public class IndexData {

    private BarSeries barSeries;
    private EmaInfo emaValues;
    private EmaIndicatorInfo emaIndicatorInfo;

    private Set<Support> supports;
    private Set<Resistance> resistances;
    private Double rsiValue;
    private TrendEnum trend;
}
