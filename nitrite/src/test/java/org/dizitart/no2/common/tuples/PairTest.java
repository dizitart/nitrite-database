package org.dizitart.no2.common.tuples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairTest {
    @Test
    public void testCanEqual() {
        assertFalse((new Pair<Object, Object>()).canEqual("other"));
    }

    @Test
    public void testEquals() {
        Pair pair = new Pair("first", "second");
        assertFalse(pair.equals(new Pair()));
    }

    @Test
    public void testEquals2() {
        Pair o = new Pair("first", "second");
        assertFalse((new Pair<Object, Object>()).equals(o));
    }

    @Test
    public void testEquals3() {
        Pair<Object, Object> pair = new Pair<Object, Object>();
        assertTrue(pair.equals(new Pair()));
    }

    @Test
    public void testEquals4() {
        Pair pair = new Pair("first", "second");
        assertTrue(pair.equals(new Pair("first", "second")));
    }

    @Test
    public void testEquals5() {
        assertFalse((new Pair<Object, Object>()).equals("o"));
    }

    @Test
    public void testEquals6() {
        Pair pair = new Pair("first", new Pair());
        assertFalse(pair.equals(new Pair("first", "second")));
    }

    @Test
    public void testEquals7() {
        Pair pair = new Pair(new Pair(), "second");
        assertFalse(pair.equals(new Pair()));
    }

    @Test
    public void testEquals8() {
        Pair pair = new Pair(null, "second");
        assertFalse(pair.equals(new Pair()));
    }

    @Test
    public void testEquals9() {
        Pair o = new Pair(null, "second");
        assertFalse((new Pair<Object, Object>()).equals(o));
    }

    @Test
    public void testHashCode() {
        assertEquals(6061, (new Pair<Object, Object>()).hashCode());
        assertEquals(547741853, (new Pair<Object, Object>("first", "second")).hashCode());
    }

    @Test
    public void testSetFirst() {
        Pair<Object, Object> pair = new Pair<Object, Object>();
        pair.setFirst("first");
        assertEquals("Pair(first=first, second=null)", pair.toString());
    }

    @Test
    public void testSetSecond() {
        Pair<Object, Object> pair = new Pair<Object, Object>();
        pair.setSecond("second");
        assertEquals("Pair(first=null, second=second)", pair.toString());
    }

    @Test
    public void testToString() {
        assertEquals("Pair(first=null, second=null)", (new Pair<Object, Object>()).toString());
    }
}

