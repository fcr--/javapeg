package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import uy.com.netlabs.javapeg.util.Pair;

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
        Grammar g = new Grammar.CatGrammar(
            new Grammar.TextGrammar("a"),
            new Grammar.TextGrammar("bb"),
            new Grammar.TextGrammar("c")
        );
        assertTrue(g.match("abbc").isMatched());
        assertTrue(g.match("abbccd").isMatched());
        ParserResult res = g.match("abb");
        assertFalse(res.isMatched());
        assertTrue(res instanceof ParserResult.Failure);
        assertEquals(3, ((ParserResult.Failure)res).idx);
        List<String> expectedTokens = ((ParserResult.Failure)res).getExpectedTokens();
        assertEquals(1, expectedTokens.size());
        assertEquals("c", expectedTokens.get(0));
        assertFalse(g.hasEpsilon());
        assertFalse(new Grammar.CatGrammar(new Grammar[]{
            new Grammar.TextGrammar("foo"),
            new Grammar.TextGrammar(""),
        }).hasEpsilon());
    }

    public void testAltGrammar() {
        Grammar g = new Grammar.AltGrammar(
            new Grammar.TextGrammar("a"),
            new Grammar.CatGrammar(
                new Grammar.TextGrammar("b"),
                new Grammar.TextGrammar("c")
            ),
            new Grammar.TextGrammar("d")
        );
        assertTrue(g.match("a").isMatched());
        assertTrue(g.match("bc").isMatched());
        assertTrue(g.match("do").isMatched());
        ParserResult eMatch = g.match("e");
        assertFalse(eMatch.isMatched());
        assertTrue(eMatch instanceof ParserResult.Failure);
        ParserResult beMatch = g.match("be");
        assertFalse(beMatch.isMatched());
        assertTrue(beMatch instanceof ParserResult.Failure);
        ParserResult.Failure beMatchFailure = (ParserResult.Failure) beMatch;
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

    public void testNumberGrammar() {
        final List<String> reducedTexts = new ArrayList<>(2);
        ReduceFunction<Integer> parseNumber = new ReduceFunction<Integer>() {
            @Override
            public List<Integer> reduce(String text, ParserResult.AstNode node, List<Integer> immutableProcessedTags) {
                List<Integer> res = new ArrayList<>();
                res.add(Integer.parseInt(node.substring(text)));
                reducedTexts.add(node.substring(text));
                return res;
            }
        };
        // T type is required on leaf grammars
        Grammar<Integer> g =
            new Grammar.CatGrammar<>(
                new Grammar.QuantGrammar<>(1, Integer.MAX_VALUE,
                    new Grammar.RangeGrammar<Integer>('0', '9')
                ).with(parseNumber),
                new Grammar.NegativeLookAhead<>(
                    new Grammar.DotGrammar<Integer>()
                )
            );
        Pair<ParserResult, List<Integer>> m = g.matchProcessing("42");
        assertTrue(m.getLeft().isMatched());
        assertTrue(m.getLeft() instanceof ParserResult.AstNode);
        ParserResult.AstNode node = (ParserResult.AstNode) m.getLeft();
        assertEquals(2, node.getLength());
        assertEquals(1, m.getRight().size());
        assertEquals(Integer.valueOf(42), m.getRight().get(0));
        assertFalse((m = g.matchProcessing("300!")).getLeft().isMatched());
        assertEquals(0, m.getRight().size());
        assertEquals(Arrays.asList(new String[]{"42", "300"}), reducedTexts);
    }

    public void testAaaBbb() {
        Grammar.MutableReferenceGrammar abThunk = new Grammar.MutableReferenceGrammar<>();
        Grammar ab = new Grammar.QuantGrammar(0, 1,
            new Grammar.CatGrammar(
                new Grammar.TextGrammar("a"),
                abThunk,
                new Grammar.TextGrammar("b")
            )
        );
        abThunk.setGrammar(ab);
        Grammar g = new Grammar.CatGrammar(ab, new Grammar.NegativeLookAhead(new Grammar.DotGrammar()));
        assertTrue(g.match("").isMatched());
        Pair<ParserResult, List> m = g.matchProcessing("ab");
        assertTrue(m.getLeft().isMatched());
        assertTrue(g.match("aabb").isMatched());
        assertTrue(g.match("aaabbb").isMatched());
        assertFalse(g.match("aaabb").isMatched());
        assertFalse(g.match("aabbb").isMatched());
        assertFalse(g.match("aaabbbc").isMatched());
        assertFalse(g.match("aaacbbb").isMatched());
    }

    public void testLeftRecursion() {
        Exception ex = null;
        try {
            Grammar.MutableReferenceGrammar g = new Grammar.MutableReferenceGrammar();
            g.setGrammar(new Grammar.CatGrammar(g, new Grammar.TextGrammar("never reached")));
            g.match("never reached");
        } catch (IllegalStateException e) {
            ex = e;
        }
        assertNotNull(ex);
        assertEquals("Left recursion detected.", ex.getMessage());
    }
}
