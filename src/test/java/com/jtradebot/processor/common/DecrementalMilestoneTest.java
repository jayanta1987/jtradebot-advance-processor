package com.jtradebot.processor.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for decremental milestone calculation logic
 */
public class DecrementalMilestoneTest {

    @Test
    public void testDecrementalMilestoneCalculation() {
        // Test case: baseMilestonePoints = 5, minMilestonePoints = 1
        // Expected sequence: 5, 4, 3, 2, 1, 1, 1, 1, ...
        
        double baseMilestonePoints = 5.0;
        double minMilestonePoints = 1.0;
        
        // Test decremental phase
        assertEquals(5.0, CommonUtils.calculateDecrementalMilestonePoints(1, baseMilestonePoints, minMilestonePoints));
        assertEquals(4.0, CommonUtils.calculateDecrementalMilestonePoints(2, baseMilestonePoints, minMilestonePoints));
        assertEquals(3.0, CommonUtils.calculateDecrementalMilestonePoints(3, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(4, baseMilestonePoints, minMilestonePoints));
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(5, baseMilestonePoints, minMilestonePoints));
        
        // Test post-decremental phase (all minMilestonePoints milestones)
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(6, baseMilestonePoints, minMilestonePoints));
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(7, baseMilestonePoints, minMilestonePoints));
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(10, baseMilestonePoints, minMilestonePoints));
    }
    
    @Test
    public void testDecrementalMilestoneWithDifferentValues() {
        // Test case: baseMilestonePoints = 3, minMilestonePoints = 1
        // Expected sequence: 3, 2, 1, 1, 1, 1, ...
        
        double baseMilestonePoints = 3.0;
        double minMilestonePoints = 1.0;
        
        assertEquals(3.0, CommonUtils.calculateDecrementalMilestonePoints(1, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(2, baseMilestonePoints, minMilestonePoints));
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(3, baseMilestonePoints, minMilestonePoints));
        assertEquals(1.0, CommonUtils.calculateDecrementalMilestonePoints(4, baseMilestonePoints, minMilestonePoints));
    }
    
    @Test
    public void testDecrementalMilestoneEdgeCases() {
        double baseMilestonePoints = 5.0;
        double minMilestonePoints = 1.0;
        
        // Test edge cases
        assertEquals(0.0, CommonUtils.calculateDecrementalMilestonePoints(0, baseMilestonePoints, minMilestonePoints));
        assertEquals(0.0, CommonUtils.calculateDecrementalMilestonePoints(-1, baseMilestonePoints, minMilestonePoints));
    }
    
    @Test
    public void testDecrementalMilestoneWithEqualBaseAndMin() {
        // Test case: baseMilestonePoints = 2, minMilestonePoints = 2
        // Expected sequence: 2, 2, 2, 2, ...
        
        double baseMilestonePoints = 2.0;
        double minMilestonePoints = 2.0;
        
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(1, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(2, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(3, baseMilestonePoints, minMilestonePoints));
    }
    
    @Test
    public void testDecrementalMilestoneWithDifferentMinValues() {
        // Test case: baseMilestonePoints = 5, minMilestonePoints = 2
        // Expected sequence: 5, 4, 3, 2, 2, 2, 2, ...
        
        double baseMilestonePoints = 5.0;
        double minMilestonePoints = 2.0;
        
        // Decremental phase
        assertEquals(5.0, CommonUtils.calculateDecrementalMilestonePoints(1, baseMilestonePoints, minMilestonePoints));
        assertEquals(4.0, CommonUtils.calculateDecrementalMilestonePoints(2, baseMilestonePoints, minMilestonePoints));
        assertEquals(3.0, CommonUtils.calculateDecrementalMilestonePoints(3, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(4, baseMilestonePoints, minMilestonePoints));
        
        // Constant phase (using minMilestonePoints)
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(5, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(6, baseMilestonePoints, minMilestonePoints));
        assertEquals(2.0, CommonUtils.calculateDecrementalMilestonePoints(10, baseMilestonePoints, minMilestonePoints));
    }
}
