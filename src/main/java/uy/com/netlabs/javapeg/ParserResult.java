/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author fran
 */
public abstract class ParserResult {

    public abstract int getIdx();

    public abstract boolean isMatched();

    public static class AstNode extends ParserResult {

        private int idx, length;
        private AstNode[] children;

        public AstNode(int idx, int length) {
            this.idx = idx;
            this.length = length;
            this.children = new AstNode[0];
        }

        public AstNode(int idx, int length, AstNode[] children) {
            this.idx = idx;
            this.length = length;
            this.children = children;
        }

        @Override
        public int getIdx() {
            return idx;
        }

        public int getLength() {
            return length;
        }

        public AstNode[] getChildren() {
            return children;
        }

        @Override
        public boolean isMatched() {
            return true;
        }

        public String substring(String text) {
            return text.substring(idx, idx + length);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("AstNode{idx=").append(idx)
                    .append(", length=").append(length).append(", children=[");
            int i = 0;
            for (AstNode child: children) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(child.toString());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class Failure extends ParserResult {

        int idx;
        List<String> expectedTokens;

        public Failure(int idx, String expectedToken) {
            this.idx = idx;
            this.expectedTokens = new ArrayList<String>(1);
            this.expectedTokens.add(expectedToken);
        }

        public Failure(int idx, List<String> expectedTokens) {
            this.idx = idx;
            this.expectedTokens = expectedTokens;
        }

        @Override
        public int getIdx() {
            return idx;
        }

        public List<String> getExpectedTokens() {
            return expectedTokens;
        }

        @Override
        public boolean isMatched() {
            return false;
        }

        /**
         * Merges two failures, giving a single representative result.
         *
         * @param e1 may be null
         * @param e2 may be null
         * @return a new ExpectingFailure instance (unless both e1 and e2 are null, in which case it returns null)
         */
        public static Failure merge(Failure e1, Failure e2) {
            if (e1 == null) {
                return e2;
            } else if (e2 == null || e1.getIdx() > e2.getIdx()) {
                return e1;
            } else if (e1.getIdx() < e2.getIdx()) {
                return e2;
            }
            ArrayList<String> expectedTokens = new ArrayList<String>(e1.getExpectedTokens().size()
                    + e2.getExpectedTokens().size());
            expectedTokens.addAll(e1.getExpectedTokens());
            expectedTokens.addAll(e2.getExpectedTokens());
            return new Failure(e1.getIdx(), expectedTokens);
        }

        @Override
        public String toString() {
            return "Failure{" + "idx=" + idx + ", expectedTokens=" + expectedTokens + '}';
        }
    }
}
