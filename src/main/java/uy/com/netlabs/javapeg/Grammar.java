/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author fran
 */
public abstract class Grammar {

    public final int WARNING_UNDEFINED_THUNK = 1;
    public final int WARNING_LEFT_RECURSION = 2;
    public final int WARNING_EPSILON_QUANTIFICATION = 4;

    protected Object tag = null;
    private Boolean hasEpsilon = null;

    public final ParserResult match(String text) {
        return match(text, 0, new Options());
    }

    protected abstract ParserResult match(String text, int idx, Options opts);

    public boolean hasEpsilon() {
        if (hasEpsilon != null) {
            return hasEpsilon;
        }
        final HashMap<Grammar, Integer> processing = new HashMap<Grammar, Integer>();
        final Function2<Grammar, Integer, Boolean> proxy = new Function2<Grammar, Integer, Boolean>() {

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

    protected static interface Function2<A, B, R> {

        public R apply(A a, B b);
    }

    protected abstract boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount);

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public static class Options {
    }

    public static class TextGrammar extends Grammar {

        private final String text;

        public TextGrammar(String text) {
            this.text = text;
        }

        @Override
        public ParserResult match(String text, int idx, Options opts) {
            int endIdx = idx + this.text.length();
            if (endIdx <= text.length() && text.substring(idx, endIdx).equals(this.text)) {
                return new ParserResult.AstNode(idx, this.text.length());
            } else {
                return new ParserResult.ExpectingFailure(idx, this.text);
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return text.length() == 0;
        }
    }

    public static class DotGrammar extends Grammar {

        @Override
        protected ParserResult match(String text, int idx, Options opts) {
            if (idx < text.length()) {
                return new ParserResult.AstNode(idx, 1);
            } else {
                return new ParserResult.ExpectingFailure(idx, "any char");
            }
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return false;
        }
    }

    public static class CatGrammar extends Grammar {

        private final Grammar[] children;

        public CatGrammar(Grammar[] children) {
            this.children = children;
            assert children.length > 0;
        }

        @Override
        protected ParserResult match(String text, int idx, Options opts) {
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
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            for (Grammar child: children) {
                if (!child.hasEpsilon()) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class AltGrammar extends Grammar {

        private Grammar[] children;

        public AltGrammar(Grammar[] children) {
            this.children = children;
            assert children.length > 0;
        }

        @Override
        protected ParserResult match(String text, int idx, Options opts) {
            ParserResult.ExpectingFailure failedRes = null;
            for (int i = 0; i < children.length; i++) {
                ParserResult res = children[i].match(text, idx, opts);
                if (res instanceof ParserResult.AstNode) {
                    ParserResult.AstNode node = (ParserResult.AstNode) res;
                    return new ParserResult.AstNode(idx, node.getLength(), new ParserResult.AstNode[]{node});
                } else if (res instanceof ParserResult.ExpectingFailure) {
                    ParserResult.ExpectingFailure failure = (ParserResult.ExpectingFailure) res;
                    failedRes = ParserResult.ExpectingFailure.merge(failedRes, failure);
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return failedRes;
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            for (Grammar child: children) {
                if (child.hasEpsilon()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class QuantGrammar extends Grammar {

        int min, max;
        Grammar child;

        public QuantGrammar(int min, int max, Grammar child) {
            this.min = min;
            this.max = max;
            this.child = child;
            assert max > min;
            assert min > 0;
        }

        @Override
        protected ParserResult match(String text, int idx, Options opts) {
            List<ParserResult.AstNode> nodes = new ArrayList<ParserResult.AstNode>(Math.min(max, 10));
            while (nodes.size() < max) {
                ParserResult res = child.match(text, idx, opts);
                if (res instanceof ParserResult.AstNode) {
                    ParserResult.AstNode node = (ParserResult.AstNode) res;
                    nodes.add(node);
                    idx += node.getLength();
                    if (node.getLength() == 0 && max == Integer.MAX_VALUE) {
                        throw new IllegalStateException("infinite loop after infinite epsilon match");
                    }
                } else if (res instanceof ParserResult.ExpectingFailure) {
                    if (nodes.size() < min) {
                        return res;
                    }
                    break;
                } else {
                    throw new IllegalStateException("invalid match response type");
                }
            }
            return new ParserResult.AstNode(nodes.get(0).getIdx(), idx,
                    nodes.toArray(new ParserResult.AstNode[nodes.size()]));
        }

        @Override
        protected boolean hasEpsilon(Function2<Grammar, Integer, Boolean> proxy, Integer nonEmptyCount) {
            return min == 0 || child.hasEpsilon();
        }

    }
}
