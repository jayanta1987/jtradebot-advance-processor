package com.jtradebot.processor.rule;

import com.jtradebot.processor.repository.document.EntryRule;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class RuleEvaluator {

    public static boolean evaluate(Object target, EntryRule rule, Map<String, Object> computedValues) throws Exception {
        List<EntryRule.Condition> conditions = rule.getConditions();
        boolean result = rule.getCombineWith().equalsIgnoreCase("AND");

        for (EntryRule.Condition condition : conditions) {
            Object fieldValue;

            // Check if the value is a dynamic field
            if (computedValues.containsKey(condition.getField())) {
                fieldValue = computedValues.get(condition.getField());
            } else {
                // Use reflection to get the field value from the target object
                fieldValue = extractFieldValue(target, condition.getField());
            }

            Object comparisonValue = condition.getValue();

            // If the comparison value is a dynamic field
            if (comparisonValue instanceof String comparisonFieldName) {
                if (computedValues.containsKey(comparisonFieldName)) {
                    comparisonValue = computedValues.get(comparisonFieldName);
                } else {
                    // Use reflection to get the field value from the target object
                    comparisonValue = extractFieldValue(target, comparisonFieldName);
                }
            }

            boolean conditionResult = evaluateCondition(fieldValue, comparisonValue, condition.getOperator());

            if (rule.getCombineWith().equalsIgnoreCase("AND") && !conditionResult) {
                return false;
            } else if (rule.getCombineWith().equalsIgnoreCase("OR") && conditionResult) {
                return true;
            }
        }
        return result;
    }

    private static Object extractFieldValue(Object target, String condition) throws NoSuchFieldException, IllegalAccessException {
        Object fieldValue;
        Field field = target.getClass().getDeclaredField(condition);
        field.setAccessible(true);
        fieldValue = field.get(target);
        return fieldValue;
    }


    private static boolean evaluateCondition(Object fieldValue, Object comparisonValue, String operator) {
        return switch (operator) {
            case ">" -> ((Number) fieldValue).doubleValue() > ((Number) comparisonValue).doubleValue();
            case "<" -> ((Number) fieldValue).doubleValue() < ((Number) comparisonValue).doubleValue();
            case ">=" -> ((Number) fieldValue).doubleValue() >= ((Number) comparisonValue).doubleValue();
            case "<=" -> ((Number) fieldValue).doubleValue() <= ((Number) comparisonValue).doubleValue();
            case "==" -> fieldValue.equals(comparisonValue);
            case "!=" -> !fieldValue.equals(comparisonValue);
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }
}
