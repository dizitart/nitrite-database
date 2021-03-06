package org.dizitart.no2.common.tuples;

import org.junit.Test;

import static org.junit.Assert.*;

public class QuartetTest {
    @Test
    public void testCanEqual() {
        assertFalse((new Quartet<>()).canEqual("other"));
    }

    @Test
    public void testEquals() {
        assertFalse((new Quartet<>()).equals("o"));
    }

    @Test
    public void testEquals10() {
        Quartet quartet = new Quartet("first", "second", "third", "fourth");
        assertFalse(quartet.equals(new Quartet()));
    }

    @Test
    public void testEquals11() {
        Quartet quartet = new Quartet("first", "second", null, "fourth");
        assertFalse(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals12() {
        Quartet quartet = new Quartet(new Quartet(), "second", "third", "fourth");
        assertFalse(quartet.equals(new Quartet()));
    }

    @Test
    public void testEquals13() {
        Quartet quartet = new Quartet("first", "second", "third", new Quartet());
        assertFalse(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals2() {
        Quartet quartet = new Quartet("first", "second", "third", null);
        assertFalse(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals3() {
        Quartet o = new Quartet(null, "second", "third", "fourth");
        assertFalse((new Quartet<>()).equals(o));
    }

    @Test
    public void testEquals4() {
        Quartet quartet = new Quartet("first", new Quartet(), "third", "fourth");
        assertFalse(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals5() {
        Quartet<Object, Object, Object, Object> quartet = new Quartet<>();
        assertTrue(quartet.equals(new Quartet()));
    }

    @Test
    public void testEquals6() {
        Quartet quartet = new Quartet("first", "second", new Quartet(), "fourth");
        assertFalse(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals7() {
        Quartet quartet = new Quartet(null, "second", "third", "fourth");
        assertFalse(quartet.equals(new Quartet()));
    }

    @Test
    public void testEquals8() {
        Quartet quartet = new Quartet("first", "second", "third", "fourth");
        assertTrue(quartet.equals(new Quartet("first", "second", "third", "fourth")));
    }

    @Test
    public void testEquals9() {
        Quartet o = new Quartet("first", "second", "third", "fourth");
        assertFalse((new Quartet<>()).equals(o));
    }

    @Test
    public void testSetFirst() {
        Quartet<Object, Object, Object, Object> quartet = new Quartet<>();
        quartet.setFirst("first");
        assertEquals("Quartet(first=first, second=null, third=null, fourth=null)", quartet.toString());
    }

    @Test
    public void testSetFourth() {
        Quartet<Object, Object, Object, Object> quartet = new Quartet<>();
        quartet.setFourth("fourth");
        assertEquals("Quartet(first=null, second=null, third=null, fourth=fourth)", quartet.toString());
    }

    @Test
    public void testSetSecond() {
        Quartet<Object, Object, Object, Object> quartet = new Quartet<>();
        quartet.setSecond("second");
        assertEquals("Quartet(first=null, second=second, third=null, fourth=null)", quartet.toString());
    }

    @Test
    public void testSetThird() {
        Quartet<Object, Object, Object, Object> quartet = new Quartet<>();
        quartet.setThird("third");
        assertEquals("Quartet(first=null, second=null, third=third, fourth=null)", quartet.toString());
    }

    @Test
    public void testToString() {
        assertEquals("Quartet(first=null, second=null, third=null, fourth=null)",
            (new Quartet<>()).toString());
    }
}

