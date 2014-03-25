/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import uy.com.netlabs.javapeg.util.Function2;

/**
 *
 * @author fran
 * @param <T> tag's generic type
 */
public abstract class Grammar<T> {

    public final int WARNING_UNDEFINED_THUNK = 1;
    public final int WARNING_LEFT_RECURSION = 2;
    public final int WARNING_EPSILON_QUANTIFICATION = 4;

    protected ReduceFunction<T> reduceFunction = null;
    private Boolean hasEpsilon = null;

    public Grammar<T> with(ReduceFunction<T> f) {
        reduceFunction = f;
        return this;
    }

    //======================================================================
    // MATCH SECTION:
    //======================================================================
    public final ParserResult match(String text) {
        return match(text, null);
    }

    public final ParserResult match(String text, ArrayList<T> tags) {
        Options opts = this.new Options();
        if (tags == null) {
            return match(text, 0, opts);
        }
        opts.processedTags = tags;
        tags.clear();
        ParserResult res = match(text, 0, opts);
        tags.addAll(opts.processedTags);
        return res;
    }

    protected ParserResult match(String text, int idx, Options opts) {
        Integer prevIdx = opts.positionsForLeftRecursionDetection.get(this);
        if (prevIdx != null && prevIdx == idx) {
            throw new IllegalStateException("Left recursion detected.");
        }
        opts.positionsForLeftRecursionDetection.put(this, idx);
        if (opts.processedTags == null) {
            ParserResult res = matchImpl(text, idx, opts);
            opts.positionsForLeftRecursionDetection.put(this, prevIdx);
            return res;
        }
        List<T> oldTags = opts.processedTags;
        ParserResult res = matchImpl(text, idx, opts);
        System.out.println(":text=" + text + ", idx=" + idx + ", res=" + res + ", rf=" + reduceFunction + ", tags=" + opts.processedTags);
        if (res instanceof ParserResult.AstNode) {
            if (reduceFunction != null) {
                opts.processedTags = reduceFunction.reduce(text, (ParserResult.AstNode) res, opts.processedTags);
            }
        } else {
            opts.processedTags = oldTags;
        }
        opts.positionsForLeftRecursionDetection.put(this, prevIdx);
        return res;
    }

    protected abstract ParserResult matchImpl(String text, int idx, Options opts);

    //======================================================================
    // HAS_EPSILON SECTION:
    //======================================================================
    public boolean hasEpsilon() {
        if (hasEpsilon != null) {
            return hasEpsilon;
        }
        final HashMap<Grammar<T>, Integer> processing = new HashMap<>();
        final Function2<Grammar<T>, Integer, Boolean> proxy = new Function2<Grammar<T>, Integer, Boolean>() {
            @Override
            public Boolean apply(Grammar<T> g, Integer nonEmptyCount) {
                Integer previousNonEmptyCount = processing.get(g);
                if (previousNonEmptyCount != null) {
                    return nonEmptyCount != previousNonEmptyCount;
                }
                processing.put(g, nonEmptyCount);
                hasEpsilon = hasEpsilon(this, nonEmptyCount);
                processing.remove(g);
                return hasEpsilon;
            }
        };
        return hasEpsilon(proxy, 0);
    }

    protected abstract boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount);

    protected class Options {

        public List<T> processedTags = null;
        public HashMap<Grammar<T>, Integer> positionsForLeftRecursionDetection = new HashMap<>();
    }

    //======================================================================
    // CONCRETE GRAMMAR IMPLEMENTATIONS
    //======================================================================
    public static class TextGrammar<T> extends Grammar<T> {

        private final String text;

        public TextGrammar(String text) {
            this.text = text;
        }

        @Override
        public ParserResult matchImpl(String text, int idx, Options opts) {
            int endIdx = idx + this.text.length();
            ParserResult res;
            if (endIdx <= text.length() && text.substring(idx, endIdx).equals(this.text)) {
                return new ParserResult.AstNode(idx, this.text.length());
            } else {
                return new ParserResult.Failure(idx, this.text);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return text.length() == 0;
        }
    }

    public static class DotGrammar<T> extends Grammar<T> {

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            if (idx < text.length()) {
                return new ParserResult.AstNode(idx, 1);
            } else {
                return new ParserResult.Failure(idx, "any char");
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return false;
        }
    }

    public static class RangeGrammar<T> extends Grammar<T> {

        private final char from, to;

        public RangeGrammar(char from, char to) {
            this.from = from;
            this.to = to;
            assert from <= to;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            if (idx >= text.length()) {
                return new ParserResult.Failure(idx, "char from '" + from + "' to '" + to + "'");
            }
            char c = text.charAt(idx);
            if (from <= c && c <= to) {
                return new ParserResult.AstNode(idx, 1);
            } else {
                return new ParserResult.Failure(idx, "char from '" + from + "' to '" + to + "'");
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return false;
        }
    }

    public static class CatGrammar<T> extends Grammar<T> {

        private final Grammar<T>[] children;

        public CatGrammar(Grammar<T>... children) {
            this.children = children;
            assert children.length > 0;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            int length = 0;
            ParserResult.AstNode[] nodes = new ParserResult.AstNode[children.length];
            for (int i = 0; i < children.length; i++) {
                ParserResult res = children[i].match(text, idx + length, opts);
                if (!(res instanceof ParserResult.AstNode)) {
                    return res;
                } else if (res instanceof ParserResult.AstNode) {
                    nodes[i] = (ParserResult.AstNode) res;
                    length += nodes[i].getLength();
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new ParserResult.AstNode(idx, length, nodes);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            for (Grammar<T> child: children) {
                if (!child.hasEpsilon()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class AltGrammar<T> extends Grammar<T> {

        private final Grammar<T>[] children;

        public AltGrammar(Grammar<T>... children) {
            this.children = children;
            assert children.length > 0;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            ParserResult.Failure failedRes = null;
            for (Grammar<T> child: children) {
                ParserResult res = child.match(text, idx, opts);
                if (res instanceof ParserResult.AstNode) {
                    ParserResult.AstNode node = (ParserResult.AstNode) res;
                    return new ParserResult.AstNode(idx, node.getLength(), new ParserResult.AstNode[]{node});
                } else if (res instanceof ParserResult.Failure) {
                    ParserResult.Failure failure = (ParserResult.Failure) res;
                    failedRes = ParserResult.Failure.merge(failedRes, failure);
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return failedRes;
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            for (Grammar<T> child: children) {
                if (child.hasEpsilon()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class QuantGrammar<T> extends Grammar<T> {

        private final int min, max;
        private final Grammar<T> child;

        public QuantGrammar(int min, int max, Grammar<T> child) {
            this.min = min;
            this.max = max;
            this.child = child;
            assert max > min;
            assert min >= 0;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            int initialIdx = idx;
            List<ParserResult.AstNode> nodes = new ArrayList<>(Math.min(max, 10));
            while (nodes.size() < max) {
                ParserResult res = child.match(text, idx, opts);
                if (res instanceof ParserResult.AstNode) {
                    ParserResult.AstNode node = (ParserResult.AstNode) res;
                    nodes.add(node);
                    idx += node.getLength();
                    if (node.getLength() == 0 && max == Integer.MAX_VALUE) {
                        throw new IllegalStateException("infinite loop after infinite epsilon match");
                    }
                } else if (res instanceof ParserResult.Failure) {
                    if (nodes.size() < min) {
                        return res;
                    }
                    break;
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new ParserResult.AstNode(initialIdx, idx - initialIdx, nodes.toArray(new ParserResult.AstNode[nodes.size()]));
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return min == 0 || child.hasEpsilon();
        }
    }

    public static class PositiveLookAhead<T> extends Grammar<T> {

        private final Grammar<T> child;

        public PositiveLookAhead(Grammar<T> child) {
            this.child = child;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            ParserResult res = child.match(text, idx, opts);
            if (res instanceof ParserResult.AstNode) {
                return new ParserResult.AstNode(idx, 0, new ParserResult.AstNode[]{(ParserResult.AstNode) res});
            } else {
                return res;
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return true;
        }
    }

    public static class NegativeLookAhead<T> extends Grammar<T> {

        private final Grammar<T> child;

        public NegativeLookAhead(Grammar<T> child) {
            this.child = child;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            ParserResult res = child.match(text, idx, opts);
            if (res instanceof ParserResult.AstNode) {
                return new ParserResult.Failure(idx, "<negative lookahead>");
            } else {
                return new ParserResult.AstNode(idx, 0);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return true;
        }
    }

    public static class MutableReferenceGrammar<T> extends Grammar<T> {

        private Grammar<T> child;

        public MutableReferenceGrammar() {
        }

        public void setGrammar(Grammar<T> child) {
            this.child = child;
        }

        @Override
        protected ParserResult matchImpl(String text, int idx, Options opts) {
            return child.match(text, idx, opts);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar<T>, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return child.hasEpsilon();
        }
    }

    public static class AltTextsGrammar<T> extends AltGrammar<T> {

        public AltTextsGrammar(String... strings) {
            super(AltTextsGrammar.<T>mapStrings(strings));
        }

        private static <T> Grammar<T>[] mapStrings(String... strings) {
            Grammar<T>[] children = new Grammar[strings.length];
            for (int i = 0; i < strings.length; i++) {
                children[i] = new TextGrammar<>(strings[i]);
            }
            return children;
        }
    }
}
