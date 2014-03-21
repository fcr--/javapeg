package uy.com.netlabs.javapeg;

import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple library.
 */
public class GrammarTest extends TestCase {

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public GrammarTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(GrammarTest.class);
    }

    public void testTextGrammar() {
        Grammar g = new Grammar.TextGrammar("foo");
        assertTrue(g.match("foo").isMatched());
        assertTrue(g.match("fooo").isMatched());
        assertFalse(g.match("fo").isMatched());
        assertFalse(g.hasEpsilon());
    }
    
    public void testDotGrammar() {
        Grammar g = new Grammar.DotGrammar();
        assertTrue(g.match("a").isMatched());
        assertTrue(g.match("aa").isMatched());
        assertFalse(g.match("").isMatched());
        assertFalse(g.hasEpsilon());
    }
    
    public void testCatGrammar() {
        Grammar g = new Grammar.CatGrammar(new Grammar[]{
            new Grammar.TextGrammar("a"),
            new Grammar.TextGrammar("bb"),
            new Grammar.TextGrammar("c"),
        });
        assertTrue(g.match("abbc").isMatched());
        assertTrue(g.match("abbccd").isMatched());
        ParserResult res = g.match("abb");
        assertFalse(res.isMatched());
        assertTrue(res instanceof ParserResult.ExpectingFailure);
        assertEquals(3, ((ParserResult.ExpectingFailure)res).idx);
        List<String> expectedTokens = ((ParserResult.ExpectingFailure)res).getExpectedTokens();
        assertEquals(1, expectedTokens.size());
        assertEquals("c", expectedTokens.get(0));
        assertFalse(g.hasEpsilon());
        assertFalse(new Grammar.CatGrammar(new Grammar[]{
            new Grammar.TextGrammar("foo"),
            new Grammar.TextGrammar(""),
        }).hasEpsilon());
    }
    
    public void testAltGrammar() {
        Grammar g = new Grammar.AltGrammar(new Grammar[]{
            new Grammar.TextGrammar("a"),
            new Grammar.CatGrammar(new Grammar[]{
                new Grammar.TextGrammar("b"),
                new Grammar.TextGrammar("c")
            }),
            new Grammar.TextGrammar("d")
        });
        assertTrue(g.match("a").isMatched());
        assertTrue(g.match("bc").isMatched());
        assertTrue(g.match("do").isMatched());
        ParserResult eMatch = g.match("e");
        assertFalse(eMatch.isMatched());
        assertTrue(eMatch instanceof ParserResult.ExpectingFailure);
        ParserResult beMatch = g.match("be");
        assertFalse(beMatch.isMatched());
        assertTrue(beMatch instanceof ParserResult.ExpectingFailure);
        ParserResult.ExpectingFailure beMatchFailure = (ParserResult.ExpectingFailure) beMatch;
        assertEquals(1, beMatchFailure.getExpectedTokens().size());
        assertEquals("c", beMatchFailure.getExpectedTokens().get(0));
        assertEquals(1, beMatch.getIdx());
        assertEquals(0, eMatch.getIdx());
        assertFalse(g.hasEpsilon());
        assertTrue(new Grammar.AltGrammar(new Grammar[]{
            new Grammar.TextGrammar("foo"),
            new Grammar.TextGrammar(""),
        }).hasEpsilon());
    }
}
