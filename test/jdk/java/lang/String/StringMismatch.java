import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;


/**
 * @test
 * @run testng StringMismatch
 */
public class StringMismatch {

    public static final int EXACT_MATCH = -1;

    @Test
    public void selfShouldMatch() {
        String str = "string";
        assertEquals(str.mismatch( 0, str.length(), str, 0, str.length()),
                EXACT_MATCH);
    }

    @Test
    public void copyShouldMatch() {
        String same = "string";
        String copy = new StringBuilder(same).toString();
        assertEquals(same.mismatch( 0, same.length(), copy, 0, copy.length()),
                EXACT_MATCH);
    }

    @Test
    public void fullPrefix() {
        String str = "string";
        String prefix = "stri";
        assertEquals(str.mismatch( 0, str.length(), prefix, 0, prefix.length()),
                prefix.length());
    }

    @Test
    public void partialPrefix() {
        String str = "string";
        String prefix = "stra";
        assertEquals(str.mismatch( 0, str.length(), prefix, 0, prefix.length()),
                "str".length());
    }

    @Test
    public void noMatch() {
        String str = "string";
        String prefix = "ztring";
        assertEquals(str.mismatch( 0, str.length(), prefix, 0, prefix.length()),
                0);
    }

    @Test
    public void suffix() {
        String str = "string";
        String suffix = "ring";
        assertEquals(str.mismatch(2, str.length(), suffix, 0, suffix.length()),
                EXACT_MATCH);
    }

    @Test
    public void substring() {
        String str = "string";
        String substring = "longstrings";
        assertEquals(str.mismatch(0, str.length(), substring, 4, substring.length()-1),
                EXACT_MATCH);
    }

    @Test
    public void tooShort() {
        String str = "string";
        String longString = "strings";
        assertEquals(str.mismatch(0, str.length(), longString, 0, longString.length()),
                str.length());
    }

    @Test
    public void isoU16Suffix() {
        String iso = "string";
        String u16 = "\u2660ring";
        assertEquals(iso.mismatch(2, iso.length(), u16, 1, u16.length()),
                EXACT_MATCH);
    }

    @Test
    public void u16IsoSuffix() {
        String u16 = "\u2660string";
        String iso = "ring";
        assertEquals(u16.mismatch(3, u16.length(), iso, 0, iso.length()),
                EXACT_MATCH);
    }

    @Test
    public void isoU16Prefix() {
        String iso = "string_ascii";
        String u16 = "string_\u2660";
        assertEquals(iso.mismatch( 0, iso.length(), u16, 0, u16.length()),
                "string_".length());
    }

    @Test
    public void u16IsoPrefix() {
        String iso = "string_ascii";
        String u16 = "string_\u2660";
        assertEquals(u16.mismatch(0, u16.length(), iso, 0, iso.length()),
                "string_".length());
    }

    @Test
    public void u16u16Prefix() {
        String u16 = "string_\u2660";
        String prefix = "string_\u03c0";
        assertEquals(u16.mismatch(0, u16.length(), prefix, 0, prefix.length()),
                "string_".length());
    }

    @Test
    public void compareToLL() {
        assertEquals("abc".compareTo("abc"), 0);
        assertTrue("abc".compareTo("ab") > 0);
        assertTrue("ab".compareTo("abc") < 0);
        assertFalse("abcd".compareTo("abca") < 0);
        assertFalse("abca".compareTo("abcd") > 0);
    }

    @Test
    public void compareToUU() {
        assertEquals("\u2660a".compareTo("\u2660a"), 0);
        assertTrue("\u2660a".compareTo("\u2660b") < 0);
        assertFalse("\u2660b".compareTo("\u2660a") < 0);
        assertEquals("\uff21".compareTo("\uff21\uff22"), -1);
    }

    @Test
    public void compareToUL() {
        assertTrue("aa\u2660".compareTo("ab") < 0);
        assertFalse("ab\u2660".compareTo("aa") < 0);
        assertEquals("A\uff21".compareTo("AA"), 65248);
    }

    @Test
    public void compareToLU() {
        assertTrue("a".compareTo("a\u2660") < 0);
        assertTrue("b".compareTo("a\u2660") > 0);
        assertEquals("ABCD".compareTo("ABCD\uff21\uff21"), -2);
    }

    @Test
    public void startsWith() {
        assertTrue("abc".startsWith("a"));
        assertTrue("abc".startsWith("ab"));
        assertTrue("abc".startsWith("abc"));
        assertFalse("abc".startsWith("abcd"));
        assertFalse("abc".startsWith("bcda"));
    }

    @Test
    public void regionMatches() {
        assertTrue("abc".regionMatches(0, "a", 0, 1));
        assertFalse("abc".regionMatches(0, "b", 0, 1));
        assertTrue("abc".regionMatches(1, "bc", 0, 2));
        assertFalse("abc".regionMatches(2, "cd", 0, 2));
    }
}
