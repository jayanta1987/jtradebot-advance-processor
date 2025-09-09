package com.jtradebot.processor.service.order;

import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.indicator.Resistance;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportResistanceData {
    private double targetPoints;
    private Set<Support> supports;
    private Set<Resistance> resistances;
    private Double nearestSupport;
    private Double nearestResistance;
}
