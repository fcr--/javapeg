/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import static java.util.Collections.EMPTY_LIST;
import java.util.HashMap;
import java.util.List;
import uy.com.netlabs.javapeg.util.FastSnocList;
import uy.com.netlabs.javapeg.util.Function1;
import uy.com.netlabs.javapeg.util.Function2;
import uy.com.netlabs.javapeg.util.Pair;

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
        Options opts = new Options();
        opts.skipProcessing = true;
        return matchProcessing(text, 0, opts).getLeft();
    }

    public final Pair<ParserResult, List<T>> matchProcessing(String text) {
        return matchProcessing(text, 0, new Options());
    }

    public final Pair<ParserResult, List<T>> matchProcessing(String text, int idx, Options opts) {
        Integer prevIdx = opts.positionsForLeftRecursionDetection.get(this);
        if (prevIdx != null && prevIdx == idx) {
            throw new IllegalStateException("Left recursion detected.");
        }
        opts.positionsForLeftRecursionDetection.put(this, idx);
        Pair<ParserResult, List<T>> res = matchProcessingImpl(text, idx, opts);
//        System.out.println(":text=" + text + ", idx=" + idx + ", res=" + res + ", rf=" + reduceFunction + ", tags=" + res.getRight());
        List<T> newTags;
        if (!(res.getLeft() instanceof ParserResult.AstNode) || opts.skipProcessing) {
            newTags = EMPTY_LIST;
        } else if (reduceFunction == null) {
            newTags = res.getRight();
        } else {
            newTags = reduceFunction.reduce(text, (ParserResult.AstNode) res.getLeft(), res.getRight());
        }
        opts.positionsForLeftRecursionDetection.put(this, prevIdx);
        return new Pair<>(res.getLeft(), newTags);
    }

    protected abstract Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts);

    //======================================================================
    // HAS_EPSILON SECTION:
    //======================================================================
    public boolean hasEpsilon() {
        if (hasEpsilon != null) {
            return hasEpsilon;
        }
        final HashMap<Grammar, Integer> processing = new HashMap<>();
        final Function2<Grammar, Integer, Boolean> proxy = new Function2<Grammar, Integer, Boolean>() {
            @Override
            public Boolean apply(Grammar g, Integer nonEmptyCount) {
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

    protected abstract boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount);

    protected static class Options {

        public HashMap<Grammar, Integer> positionsForLeftRecursionDetection = new HashMap<>();
        public boolean skipProcessing = false;
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
        public Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            int endIdx = idx + this.text.length();
            ParserResult res;
            if (endIdx <= text.length() && text.substring(idx, endIdx).equals(this.text)) {
                return new Pair(new ParserResult.AstNode(idx, this.text.length()), EMPTY_LIST);
            } else {
                return new Pair(new ParserResult.Failure(idx, this.text), EMPTY_LIST);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return text.length() == 0;
        }
    }

    public static class DotGrammar<T> extends Grammar<T> {

        @Override
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            if (idx < text.length()) {
                return new Pair(new ParserResult.AstNode(idx, 1), EMPTY_LIST);
            } else {
                return new Pair(new ParserResult.Failure(idx, "any char"), EMPTY_LIST);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            if (idx >= text.length()) {
                return new Pair(new ParserResult.Failure(idx, "char from '" + from + "' to '" + to + "'"), EMPTY_LIST);
            }
            char c = text.charAt(idx);
            if (from <= c && c <= to) {
                return new Pair(new ParserResult.AstNode(idx, 1), EMPTY_LIST);
            } else {
                return new Pair(new ParserResult.Failure(idx, "char from '" + from + "' to '" + to + "'"), EMPTY_LIST);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            int length = 0;
            List<T> tags = EMPTY_LIST;
            ParserResult.AstNode[] nodes = new ParserResult.AstNode[children.length];
            for (int i = 0; i < children.length; i++) {
                Pair<ParserResult, List<T>> res = children[i].matchProcessing(text, idx + length, opts);
                if (!(res.getLeft() instanceof ParserResult.AstNode)) {
                    return res;
                } else if (res.getLeft() instanceof ParserResult.AstNode) {
                    nodes[i] = (ParserResult.AstNode) res.getLeft();
                    tags = FastSnocList.snocAll(tags, res.getRight());
                    length += nodes[i].getLength();
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new Pair(new ParserResult.AstNode(idx, length, nodes), tags);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            ParserResult.Failure failedRes = null;
            for (Grammar<T> child: children) {
                Pair<ParserResult, List<T>> res = child.matchProcessing(text, idx, opts);
                if (res.getLeft().isMatched()) {
                    return res;
                } else if (res.getLeft() instanceof ParserResult.Failure) {
                    ParserResult.Failure failure = (ParserResult.Failure) res.getLeft();
                    failedRes = ParserResult.Failure.merge(failedRes, failure);
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new Pair(failedRes, EMPTY_LIST);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            int initialIdx = idx;
            List<T> tags = EMPTY_LIST;
            List<ParserResult.AstNode> nodes = new ArrayList<>(Math.min(max, 10));
            while (nodes.size() < max) {
                Pair<ParserResult, List<T>> res = child.matchProcessing(text, idx, opts);
                if (res.getLeft() instanceof ParserResult.AstNode) {
                    ParserResult.AstNode node = (ParserResult.AstNode) res.getLeft();
                    nodes.add(node);
                    tags = FastSnocList.snocAll(tags, res.getRight());
                    idx += node.getLength();
                    if (node.getLength() == 0 && max == Integer.MAX_VALUE) {
                        throw new IllegalStateException("infinite loop after infinite epsilon match");
                    }
                } else if (res.getLeft() instanceof ParserResult.Failure) {
                    if (nodes.size() < min) {
                        return res;
                    }
                    break;
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new Pair(new ParserResult.AstNode(initialIdx, idx - initialIdx,
                    nodes.toArray(new ParserResult.AstNode[nodes.size()])), tags);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return min == 0 || child.hasEpsilon();
        }
    }

    public static class PositiveLookAhead<T> extends Grammar<T> {

        private final Grammar<T> child;

        public PositiveLookAhead(Grammar<T> child) {
            this.child = child;
        }

        @Override
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            Pair<ParserResult, List<T>> res = child.matchProcessing(text, idx, opts);
            if (res.getLeft() instanceof ParserResult.AstNode) {
                return new Pair(new ParserResult.AstNode(idx, 0,
                        new ParserResult.AstNode[]{(ParserResult.AstNode) res.getLeft()}), EMPTY_LIST);
            } else {
                return res;
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return true;
        }
    }

    public static class NegativeLookAhead<T> extends Grammar<T> {

        private final Grammar<T> child;

        public NegativeLookAhead(Grammar<T> child) {
            this.child = child;
        }

        @Override
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            Pair<ParserResult, List<T>> res = child.matchProcessing(text, idx, opts);
            if (res.getLeft() instanceof ParserResult.AstNode) {
                return new Pair(new ParserResult.Failure(idx, "<negative lookahead>"), EMPTY_LIST);
            } else {
                return new Pair(new ParserResult.AstNode(idx, 0), EMPTY_LIST);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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
        protected Pair<ParserResult, List<T>> matchProcessingImpl(String text, int idx, Options opts) {
            return child.matchProcessing(text, idx, opts);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
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

    public static class AdapterGrammar<T, S> extends Grammar<S> {

        private final Grammar<T> gram;
        private final Function1<T, S> adapterFunction;

        public AdapterGrammar(Grammar<T> gram, Function1<T, S> adapterFunction) {
            this.gram = gram;
            this.adapterFunction = adapterFunction;
        }

        @Override
        protected Pair<ParserResult, List<S>> matchProcessingImpl(String text, int idx, Options opts) {
            Pair<ParserResult, List<T>> res = gram.matchProcessing(text, idx, opts);
            ArrayList<S> newTags = new ArrayList(res.getRight());
            for (T tag: res.getRight()) {
                newTags.add(adapterFunction.apply(tag));
            }
            return new Pair(res.getLeft(), newTags);
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return gram.hasEpsilon(proxy, nonEmptyCount);
        }
    }
}
